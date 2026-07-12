// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.NavigableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A publisher with asynchronous publisher confirmation tracking.
 * <p>
 * A {@code ConfirmationPublisher} owns a dedicated channel created from the
 * supplied {@link Connection}. The channel is put in confirm mode and every
 * message published through {@link #basicPublishAsync} is tracked: the
 * returned {@link CompletableFuture} completes with the caller-supplied
 * context when the broker confirms the message, or exceptionally with
 * {@link PublishException} when the broker rejects ({@code basic.nack}) or
 * returns ({@code basic.return}) it.
 * <p>
 * The publisher's channel is private to it: topology operations and ordinary
 * publishing should use the application's own channels.
 * <p>
 * When the connection supports automatic recovery, the publisher keeps
 * working across recoveries: futures outstanding at the time of a connection
 * failure complete exceptionally (their outcome is unknown; callers decide
 * whether to republish) and publishing can resume once recovery completes.
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * ConfirmationPublisher publisher = ConfirmationPublisher.create(connection, 100);
 * publisher.basicPublishAsync("exchange", "routing.key", props, body, "msg-123")
 *     .thenAccept(msgId -> System.out.println("Confirmed: " + msgId))
 *     .exceptionally(ex -> {
 *         System.err.println("Failed: " + ex.getCause());
 *         return null;
 *     });
 * }</pre>
 *
 * @see PublishException
 */
public class ConfirmationPublisher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmationPublisher.class);

    /**
     * Header used to correlate {@code basic.return} with a publish. Only added
     * to messages published with {@code mandatory = true}, since a
     * {@code basic.return} frame carries no delivery tag and this header is the
     * only way to match it back to a publish.
     * <p>
     * The broker echoes this header both in the return frame and on normal
     * delivery, so when a mandatory message <em>is</em> routable its consumers
     * receive it with this header present. It cannot be stripped on the routable
     * path. Applications that must not expose it should publish such messages
     * through their own channel rather than this publisher.
     */
    public static final String SEQUENCE_NUMBER_HEADER = "x-seq-no";

    private final Channel channel;
    private final Semaphore outstandingLimit;
    private final Executor confirmationExecutor;
    private final ReentrantLock publishLock = new ReentrantLock();
    private final ConcurrentSkipListMap<Long, OutstandingConfirmation> outstanding =
        new ConcurrentSkipListMap<Long, OutstandingConfirmation>();

    /**
     * Creates a publisher with no bound on outstanding confirmations.
     *
     * @param connection the connection to create the publisher's channel from
     * @return a new publisher
     * @throws IOException if channel creation or {@code confirm.select} fails
     */
    public static ConfirmationPublisher create(Connection connection) throws IOException {
        return new ConfirmationPublisher(connection, 0, ForkJoinPool.commonPool());
    }

    /**
     * Creates a publisher that allows at most {@code maxOutstandingConfirms}
     * unconfirmed messages; {@link #basicPublishAsync} blocks when the limit
     * is reached.
     *
     * @param connection the connection to create the publisher's channel from
     * @param maxOutstandingConfirms maximum unconfirmed messages, must be positive
     * @return a new publisher
     * @throws IOException if channel creation or {@code confirm.select} fails
     */
    public static ConfirmationPublisher create(Connection connection, int maxOutstandingConfirms)
        throws IOException {
        if (maxOutstandingConfirms <= 0) {
            throw new IllegalArgumentException("maxOutstandingConfirms must be positive: " + maxOutstandingConfirms);
        }
        return new ConfirmationPublisher(connection, maxOutstandingConfirms, ForkJoinPool.commonPool());
    }

    /**
     * Creates a publisher with a bound on outstanding confirmations and a
     * custom executor for completing futures.
     *
     * @param connection the connection to create the publisher's channel from
     * @param maxOutstandingConfirms maximum unconfirmed messages, must be positive
     * @param confirmationExecutor executor used to complete the returned futures
     *        (and thus run non-async continuations); never the connection's I/O thread
     * @return a new publisher
     * @throws IOException if channel creation or {@code confirm.select} fails
     */
    public static ConfirmationPublisher create(Connection connection, int maxOutstandingConfirms,
        Executor confirmationExecutor) throws IOException {
        if (maxOutstandingConfirms <= 0) {
            throw new IllegalArgumentException("maxOutstandingConfirms must be positive: " + maxOutstandingConfirms);
        }
        if (confirmationExecutor == null) {
            throw new IllegalArgumentException("confirmationExecutor must not be null");
        }
        return new ConfirmationPublisher(connection, maxOutstandingConfirms, confirmationExecutor);
    }

    private ConfirmationPublisher(Connection connection, int maxOutstandingConfirms,
        Executor confirmationExecutor) throws IOException {
        this.channel = connection.createChannel();
        this.outstandingLimit = maxOutstandingConfirms > 0 ? new Semaphore(maxOutstandingConfirms) : null;
        this.confirmationExecutor = confirmationExecutor;
        this.channel.confirmSelect();
        this.channel.addConfirmListener(this::handleAck, this::handleNack);
        this.channel.addReturnListener(this::handleReturn);
        this.channel.addShutdownListener(this::handleShutdown);
        if (this.channel instanceof Recoverable) {
            ((Recoverable) this.channel).addRecoveryListener(new RecoveryListener() {
                @Override
                public void handleRecoveryStarted(Recoverable recoverable) {
                    onRecoveryStarted();
                }

                @Override
                public void handleRecovery(Recoverable recoverable) {
                }
            });
        }
    }

    /**
     * Asynchronously publish a message with publisher confirmation tracking.
     * <p>
     * Thread-safe. If the publisher was created with a bound on outstanding
     * confirmations, this method blocks while the bound is reached.
     *
     * @param exchange the exchange to publish to
     * @param routingKey the routing key
     * @param props message properties (null for default)
     * @param body message body
     * @param context user-provided context object for correlation (can be null)
     * @param <T> the type of the context parameter
     * @return a future completing with the context on {@code basic.ack}, or
     *         exceptionally with {@link PublishException} on {@code basic.nack},
     *         or with the underlying exception if the publish itself fails
     */
    public <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
        AMQP.BasicProperties props, byte[] body, T context) {
        return basicPublishAsync(exchange, routingKey, false, props, body, context);
    }

    /**
     * Asynchronously publish a message with the mandatory flag and publisher
     * confirmation tracking.
     * <p>
     * When {@code mandatory} is true a {@value #SEQUENCE_NUMBER_HEADER} header
     * is added to the message so that a {@code basic.return} can be correlated
     * with this publish; the future then completes exceptionally with a
     * {@link PublishException} carrying the {@link Return}.
     *
     * @param exchange the exchange to publish to
     * @param routingKey the routing key
     * @param mandatory true if the message must be routable to at least one queue
     * @param props message properties (null for default); must not already
     *        contain a {@value #SEQUENCE_NUMBER_HEADER} header when mandatory
     * @param body message body
     * @param context user-provided context object for correlation (can be null)
     * @param <T> the type of the context parameter
     * @return a future completing with the context on {@code basic.ack}, or
     *         exceptionally with {@link PublishException} on {@code basic.nack}
     *         or {@code basic.return}, or with the underlying exception if the
     *         publish itself fails
     * @throws IllegalArgumentException if {@code mandatory} is true and
     *         {@code props} already contains a {@value #SEQUENCE_NUMBER_HEADER}
     *         header; this is a caller programming error and is reported
     *         synchronously rather than through the returned future
     */
    public <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
        boolean mandatory, AMQP.BasicProperties props, byte[] body, T context) {
        if (mandatory && props != null && props.getHeaders() != null
            && props.getHeaders().containsKey(SEQUENCE_NUMBER_HEADER)) {
            throw new IllegalArgumentException(
                "message properties must not contain a " + SEQUENCE_NUMBER_HEADER + " header");
        }
        CompletableFuture<T> future = new CompletableFuture<T>();
        OutstandingConfirmation confirmation =
            new OutstandingConfirmation(future, context, this.outstandingLimit);
        try {
            if (this.outstandingLimit != null) {
                this.outstandingLimit.acquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
            return future;
        }
        this.publishLock.lock();
        try {
            long seqNo = this.channel.getNextPublishSeqNo();
            if (seqNo == 0) {
                // The channel is transiently not in confirm mode: an
                // autorecovering channel has swapped in a fresh delegate whose
                // confirm.select has not been re-issued yet. Publishing now
                // would send an untracked message the broker never confirms.
                confirmation.releaseSlot();
                future.completeExceptionally(new IOException(
                    "channel is not in confirm mode (recovery in progress)"));
                return future;
            }
            AMQP.BasicProperties publishProps =
                mandatory ? withSequenceNumberHeader(props, seqNo) : props;
            this.outstanding.put(seqNo, confirmation);
            try {
                this.channel.basicPublish(exchange, routingKey, mandatory, publishProps, body);
            } catch (Exception e) {
                this.outstanding.remove(seqNo);
                confirmation.releaseSlot();
                abortChannelIfTagSpaceStuck(e);
                future.completeExceptionally(e);
            }
        } finally {
            this.publishLock.unlock();
        }
        return future;
    }

    /**
     * @return the number of publishes awaiting a broker confirmation; this is
     *         not a constant-time operation and is intended for monitoring, not
     *         for use on a hot path
     */
    public int getOutstandingCount() {
        return this.outstanding.size();
    }

    /**
     * Closes the publisher and its channel with a graceful handshake.
     * Confirmations that arrive before the channel finishes closing complete
     * their futures normally; any futures still outstanding once the channel is
     * closed complete exceptionally with a {@link ShutdownSignalException}
     * (their messages are unconfirmed and their outcome unknown).
     */
    @Override
    public void close() throws IOException {
        try {
            this.channel.close();
        } catch (TimeoutException e) {
            throw new IOException(e);
        } catch (AlreadyClosedException e) {
            // already closed by shutdown or a previous call
        }
    }

    private static AMQP.BasicProperties withSequenceNumberHeader(AMQP.BasicProperties props, long seqNo) {
        Map<String, Object> headers = new HashMap<String, Object>();
        if (props != null && props.getHeaders() != null) {
            headers.putAll(props.getHeaders());
        }
        headers.put(SEQUENCE_NUMBER_HEADER, seqNo);
        AMQP.BasicProperties.Builder builder =
            props == null ? new AMQP.BasicProperties.Builder() : props.builder();
        return builder.headers(headers).build();
    }

    /**
     * The delegate channel increments its publish sequence number before the
     * frame reaches the wire, so a failed publish leaves the channel's counter
     * ahead of the broker's delivery-tag sequence and every later confirmation
     * would be correlated incorrectly.
     * <p>
     * An {@link IOException} means the connection is failing: the imminent
     * shutdown fails all outstanding futures and, if the connection recovers,
     * the counter resets to 1 (the broker restarts delivery tags), so the skew
     * self-heals and the channel must be left alone to recover. Any other
     * exception (for example from a misbehaving {@code ObservationCollector})
     * leaves the channel open with a genuinely skewed counter and no shutdown
     * pending; the channel is private to this publisher, so the safe reaction
     * is to abort it, which fails all outstanding futures via the shutdown
     * listener.
     */
    private void abortChannelIfTagSpaceStuck(Exception failure) {
        if (failure instanceof IOException) {
            return;
        }
        if (this.channel.isOpen()) {
            LOGGER.warn("Aborting confirmation publisher channel: publish failed "
                + "after a sequence number was consumed", failure);
            this.channel.abort();
        }
    }

    private void handleAck(long deliveryTag, boolean multiple) {
        completeConfirmations(deliveryTag, multiple, false);
    }

    private void handleNack(long deliveryTag, boolean multiple) {
        completeConfirmations(deliveryTag, multiple, true);
    }

    private void completeConfirmations(long deliveryTag, boolean multiple, boolean nack) {
        if (multiple) {
            // Confirm/return handlers all run on the single connection thread and
            // concurrent publishes only insert higher sequence numbers, so this
            // head view is stable: complete each entry, then clear it in one pass.
            NavigableMap<Long, OutstandingConfirmation> confirmed =
                this.outstanding.headMap(deliveryTag, true);
            for (Map.Entry<Long, OutstandingConfirmation> entry : confirmed.entrySet()) {
                complete(entry.getKey(), entry.getValue(), nack);
            }
            confirmed.clear();
        } else {
            OutstandingConfirmation confirmation = this.outstanding.remove(deliveryTag);
            if (confirmation != null) {
                complete(deliveryTag, confirmation, nack);
            }
        }
    }

    private void complete(long seqNo, OutstandingConfirmation confirmation, boolean nack) {
        confirmation.releaseSlot();
        if (nack) {
            final PublishException e = new PublishException(seqNo, confirmation.context);
            dispatch(() -> confirmation.completeExceptionally(e));
        } else {
            dispatch(confirmation::complete);
        }
    }

    private void handleReturn(Return returned) {
        Long seqNo = extractSequenceNumber(returned.getProperties());
        if (seqNo == null) {
            // not published through this publisher, or header lost: the
            // subsequent basic.ack still completes the future successfully
            LOGGER.debug("Ignoring basic.return without a usable {} header", SEQUENCE_NUMBER_HEADER);
            return;
        }
        final OutstandingConfirmation confirmation = this.outstanding.remove(seqNo);
        if (confirmation == null) {
            return;
        }
        confirmation.releaseSlot();
        final PublishException e = new PublishException(seqNo, returned, confirmation.context);
        dispatch(() -> confirmation.completeExceptionally(e));
    }

    private static Long extractSequenceNumber(AMQP.BasicProperties props) {
        if (props == null || props.getHeaders() == null) {
            return null;
        }
        Object value = props.getHeaders().get(SEQUENCE_NUMBER_HEADER);
        return value instanceof Long ? (Long) value : null;
    }

    private void handleShutdown(ShutdownSignalException cause) {
        failAllOutstanding(cause);
    }

    /**
     * Fails every outstanding future exceptionally and drains the map. Called
     * both on channel shutdown and, for an autorecovering channel, at the start
     * of recovery: recovery re-issues {@code confirm.select}, which resets the
     * delivery-tag sequence to 1, so any entry that outlived the shutdown drain
     * must be failed before the reused tag space could mis-correlate it.
     */
    private void failAllOutstanding(Throwable cause) {
        Map.Entry<Long, OutstandingConfirmation> entry;
        while ((entry = this.outstanding.pollFirstEntry()) != null) {
            final OutstandingConfirmation confirmation = entry.getValue();
            confirmation.releaseSlot();
            dispatch(() -> confirmation.completeExceptionally(cause));
        }
    }

    private void onRecoveryStarted() {
        // The new delegate is already installed and open by this point, so the
        // outcome of any still-outstanding publish is unknown; fail it before
        // recovery resets the delivery-tag sequence.
        failAllOutstanding(new IOException(
            "connection recovery started; outcome of outstanding publishes is unknown"));
    }

    /**
     * Runs a completion task on the configured executor, falling back to the
     * calling thread if the executor rejects it (for example a user-supplied
     * executor that is saturated or shut down). Completing inline still runs off
     * the future's own continuations but guarantees the future never hangs.
     */
    private void dispatch(Runnable completion) {
        try {
            this.confirmationExecutor.execute(completion);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Confirmation executor rejected a completion task; "
                + "completing on the calling thread", e);
            completion.run();
        }
    }

    private static final class OutstandingConfirmation {
        private final CompletableFuture<?> future;
        private final Object context;
        private final Semaphore limit;
        private final AtomicBoolean slotReleased = new AtomicBoolean(false);

        private OutstandingConfirmation(CompletableFuture<?> future, Object context, Semaphore limit) {
            this.future = future;
            this.context = context;
            this.limit = limit;
        }

        @SuppressWarnings("unchecked")
        private void complete() {
            ((CompletableFuture<Object>) this.future).complete(this.context);
        }

        private void completeExceptionally(Throwable t) {
            this.future.completeExceptionally(t);
        }

        private void releaseSlot() {
            if (this.limit != null && this.slotReleased.compareAndSet(false, true)) {
                this.limit.release();
            }
        }
    }
}
