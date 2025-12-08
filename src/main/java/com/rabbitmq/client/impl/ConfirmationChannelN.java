// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.BasicProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of {@link ConfirmationChannel} that wraps an existing {@link Channel}
 * and provides asynchronous publisher confirmation tracking.
 * <p>
 * This class maintains its own sequence number space independent of the wrapped channel.
 * The {@link #basicPublish} methods are not supported and will throw
 * {@link UnsupportedOperationException}. Use {@link #basicPublishAsync} instead.
 */
public class ConfirmationChannelN implements ConfirmationChannel
{
    private static final String SEQUENCE_NUMBER_HEADER = "x-seq-no";

    private final Channel delegate;
    private final RateLimiter rateLimiter;
    private final AtomicLong nextSeqNo = new AtomicLong(1);
    private final Map<Long, ConfirmationEntry<?>> confirmations;

    private final ShutdownListener shutdownListener;
    private final ReturnListener returnListener;
    private final ConfirmListener confirmListener;

    /**
     * Creates a new ConfirmationChannelN wrapping the given channel.
     *
     * @param delegate the channel to wrap
     * @param rateLimiter optional rate limiter for controlling publish concurrency (can be null)
     * @throws IOException if enabling publisher confirmations fails
     */
    public ConfirmationChannelN(Channel delegate, RateLimiter rateLimiter) throws IOException
    {
        if (delegate == null)
        {
            throw new IllegalArgumentException("delegate must be non-null");
        }

        this.delegate = delegate;
        this.rateLimiter = rateLimiter;

        int initialCapacity = (rateLimiter != null) ? rateLimiter.getMaxConcurrency() : 16;
        this.confirmations = new ConcurrentHashMap<>(initialCapacity > 0 ? initialCapacity : 16);

        // Enable publisher confirmations on the delegate channel
        delegate.confirmSelect();

        // Register listeners for confirmations and returns
        // Store listener instances so we can remove them later
        this.shutdownListener = this::handleShutdown;
        this.returnListener = this::handleReturn;
        this.confirmListener = delegate.addConfirmListener(this::handleAck, this::handleNack);

        delegate.addReturnListener(returnListener);
        delegate.addShutdownListener(shutdownListener);
    }

    @Override
    public <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                                       BasicProperties props, byte[] body, T context)
    {
        return basicPublishAsync(exchange, routingKey, false, props, body, context);
    }

    @Override
    public <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                                       boolean mandatory,
                                                       BasicProperties props, byte[] body, T context)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        RateLimiter.Permit permit = null;
        long seqNo = 0;

        try
        {
            // Acquire rate limiter permit if configured
            if (rateLimiter != null)
            {
                permit = rateLimiter.acquire();
            }

            // Get next sequence number
            seqNo = nextSeqNo.getAndIncrement();

            // Store confirmation entry
            confirmations.put(seqNo, new ConfirmationEntry<>(future, permit, context));

            // Add sequence number to message headers
            if (props == null)
            {
                props = MessageProperties.MINIMAL_BASIC;
            }
            props = addSequenceNumberHeader(props, seqNo);

            // Publish to delegate channel
            // Note: Metrics are collected by the delegate's MetricsCollector
            delegate.basicPublish(exchange, routingKey, mandatory, props, body);
        }
        catch (IOException | AlreadyClosedException | InterruptedException e)
        {
            // Clean up on error
            confirmations.remove(seqNo);
            if (permit != null)
            {
                permit.release();
            }
            future.completeExceptionally(e);
        }

        return future;
    }

    // Unsupported basicPublish methods - throw UnsupportedOperationException

    @Override
    public void basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
            throws IOException
    {
        throw new UnsupportedOperationException(
                "basicPublish() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public void basicPublish(String exchange, String routingKey, boolean mandatory, BasicProperties props, byte[] body)
            throws IOException
    {
        throw new UnsupportedOperationException(
                "basicPublish() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate,
                             BasicProperties props, byte[] body)
            throws IOException
    {
        throw new UnsupportedOperationException(
                "basicPublish() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    // Delegated Channel methods

    @Override
    public int getChannelNumber()
    {
        return delegate.getChannelNumber();
    }

    @Override
    public Connection getConnection()
    {
        return delegate.getConnection();
    }

    @Override
    public void close() throws IOException, TimeoutException
    {
        delegate.close();
        removeListeners();
    }

    @Override
    public void close(int closeCode, String closeMessage) throws IOException, TimeoutException
    {
        delegate.close(closeCode, closeMessage);
        removeListeners();
    }

    @Override
    public void abort()
    {
        delegate.abort();
        removeListeners();
    }

    @Override
    public void abort(int closeCode, String closeMessage)
    {
        delegate.abort(closeCode, closeMessage);
        removeListeners();
    }

    @Override
    public void addReturnListener(ReturnListener listener)
    {
        delegate.addReturnListener(listener);
    }

    @Override
    public ReturnListener addReturnListener(ReturnCallback returnCallback)
    {
        return delegate.addReturnListener(returnCallback);
    }

    @Override
    public boolean removeReturnListener(ReturnListener listener)
    {
        return delegate.removeReturnListener(listener);
    }

    @Override
    public void clearReturnListeners()
    {
        delegate.clearReturnListeners();
    }

    @Override
    public void addConfirmListener(ConfirmListener listener)
    {
        delegate.addConfirmListener(listener);
    }

    @Override
    public ConfirmListener addConfirmListener(ConfirmCallback ackCallback, ConfirmCallback nackCallback)
    {
        return delegate.addConfirmListener(ackCallback, nackCallback);
    }

    @Override
    public boolean removeConfirmListener(ConfirmListener listener)
    {
        return delegate.removeConfirmListener(listener);
    }

    @Override
    public void clearConfirmListeners()
    {
        delegate.clearConfirmListeners();
    }

    @Override
    public Consumer getDefaultConsumer()
    {
        return delegate.getDefaultConsumer();
    }

    @Override
    public void setDefaultConsumer(Consumer consumer)
    {
        delegate.setDefaultConsumer(consumer);
    }

    @Override
    public void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException
    {
        delegate.basicQos(prefetchSize, prefetchCount, global);
    }

    @Override
    public void basicQos(int prefetchCount, boolean global) throws IOException
    {
        delegate.basicQos(prefetchCount, global);
    }

    @Override
    public void basicQos(int prefetchCount) throws IOException
    {
        delegate.basicQos(prefetchCount);
    }

    @Override
    public String basicConsume(String queue, Consumer callback) throws IOException
    {
        return delegate.basicConsume(queue, callback);
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException
    {
        return delegate.basicConsume(queue, deliverCallback, cancelCallback);
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, deliverCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, deliverCallback, cancelCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, deliverCallback, cancelCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, deliverCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, deliverCallback, cancelCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, arguments, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, cancelCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, arguments, deliverCallback, cancelCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, cancelCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, deliverCallback, cancelCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, callback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, cancelCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, shutdownSignalCallback);
    }

    @Override
    public String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, DeliverCallback deliverCallback, CancelCallback cancelCallback, ConsumerShutdownSignalCallback shutdownSignalCallback) throws IOException
    {
        return delegate.basicConsume(queue, autoAck, consumerTag, noLocal, exclusive, arguments, deliverCallback, cancelCallback, shutdownSignalCallback);
    }

    @Override
    public void basicCancel(String consumerTag) throws IOException
    {
        delegate.basicCancel(consumerTag);
    }

    @Override
    public AMQP.Basic.RecoverOk basicRecover() throws IOException
    {
        return delegate.basicRecover();
    }

    @Override
    public AMQP.Basic.RecoverOk basicRecover(boolean requeue) throws IOException
    {
        return delegate.basicRecover(requeue);
    }

    @Override
    public AMQP.Tx.SelectOk txSelect() throws IOException
    {
        return delegate.txSelect();
    }

    @Override
    public AMQP.Tx.CommitOk txCommit() throws IOException
    {
        return delegate.txCommit();
    }

    @Override
    public AMQP.Tx.RollbackOk txRollback() throws IOException
    {
        return delegate.txRollback();
    }

    @Override
    public AMQP.Confirm.SelectOk confirmSelect() throws IOException
    {
        return delegate.confirmSelect();
    }

    @Override
    public long getNextPublishSeqNo()
    {
        return delegate.getNextPublishSeqNo();
    }

    @Override
    public boolean waitForConfirms() throws InterruptedException
    {
        throw new UnsupportedOperationException(
                "waitForConfirms() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public boolean waitForConfirms(long timeout) throws InterruptedException, TimeoutException
    {
        throw new UnsupportedOperationException(
                "waitForConfirms() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public void waitForConfirmsOrDie() throws IOException, InterruptedException
    {
        throw new UnsupportedOperationException(
                "waitForConfirmsOrDie() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public void waitForConfirmsOrDie(long timeout) throws IOException, InterruptedException, TimeoutException
    {
        throw new UnsupportedOperationException(
                "waitForConfirmsOrDie() is not supported on ConfirmationChannel. Use basicPublishAsync() instead.");
    }

    @Override
    public void asyncRpc(com.rabbitmq.client.Method method) throws IOException
    {
        delegate.asyncRpc(method);
    }

    @Override
    public Command rpc(com.rabbitmq.client.Method method) throws IOException
    {
        return delegate.rpc(method);
    }

    @Override
    public long messageCount(String queue) throws IOException
    {
        return delegate.messageCount(queue);
    }

    @Override
    public long consumerCount(String queue) throws IOException
    {
        return delegate.consumerCount(queue);
    }

    @Override
    public CompletableFuture<Command> asyncCompletableRpc(com.rabbitmq.client.Method method) throws IOException
    {
        return delegate.asyncCompletableRpc(method);
    }

    @Override
    public void addShutdownListener(ShutdownListener listener)
    {
        delegate.addShutdownListener(listener);
    }

    @Override
    public void removeShutdownListener(ShutdownListener listener)
    {
        delegate.removeShutdownListener(listener);
    }

    @Override
    public ShutdownSignalException getCloseReason()
    {
        return delegate.getCloseReason();
    }

    @Override
    public void notifyListeners()
    {
        delegate.notifyListeners();
    }

    @Override
    public boolean isOpen()
    {
        return delegate.isOpen();
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable, autoDelete, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable, autoDelete, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclare(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public void exchangeDeclareNoWait(String exchange, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException
    {
        delegate.exchangeDeclareNoWait(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public void exchangeDeclareNoWait(String exchange, BuiltinExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException
    {
        delegate.exchangeDeclareNoWait(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public AMQP.Exchange.DeclareOk exchangeDeclarePassive(String exchange) throws IOException
    {
        return delegate.exchangeDeclarePassive(exchange);
    }

    @Override
    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException
    {
        return delegate.exchangeDelete(exchange, ifUnused);
    }

    @Override
    public void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException
    {
        delegate.exchangeDeleteNoWait(exchange, ifUnused);
    }

    @Override
    public AMQP.Exchange.DeleteOk exchangeDelete(String exchange) throws IOException
    {
        return delegate.exchangeDelete(exchange);
    }

    @Override
    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException
    {
        return delegate.exchangeBind(destination, source, routingKey);
    }

    @Override
    public AMQP.Exchange.BindOk exchangeBind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeBind(destination, source, routingKey, arguments);
    }

    @Override
    public void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException
    {
        delegate.exchangeBindNoWait(destination, source, routingKey, arguments);
    }

    @Override
    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey) throws IOException
    {
        return delegate.exchangeUnbind(destination, source, routingKey);
    }

    @Override
    public AMQP.Exchange.UnbindOk exchangeUnbind(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException
    {
        return delegate.exchangeUnbind(destination, source, routingKey, arguments);
    }

    @Override
    public void exchangeUnbindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException
    {
        delegate.exchangeUnbindNoWait(destination, source, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclare() throws IOException
    {
        return delegate.queueDeclare();
    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException
    {
        return delegate.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
    }

    @Override
    public void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException
    {
        delegate.queueDeclareNoWait(queue, durable, exclusive, autoDelete, arguments);
    }

    @Override
    public AMQP.Queue.DeclareOk queueDeclarePassive(String queue) throws IOException
    {
        return delegate.queueDeclarePassive(queue);
    }

    @Override
    public AMQP.Queue.DeleteOk queueDelete(String queue) throws IOException
    {
        return delegate.queueDelete(queue);
    }

    @Override
    public AMQP.Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException
    {
        return delegate.queueDelete(queue, ifUnused, ifEmpty);
    }

    @Override
    public void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException
    {
        delegate.queueDeleteNoWait(queue, ifUnused, ifEmpty);
    }

    @Override
    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException
    {
        return delegate.queueBind(queue, exchange, routingKey);
    }

    @Override
    public AMQP.Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException
    {
        return delegate.queueBind(queue, exchange, routingKey, arguments);
    }

    @Override
    public void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException
    {
        delegate.queueBindNoWait(queue, exchange, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException
    {
        return delegate.queueUnbind(queue, exchange, routingKey);
    }

    @Override
    public AMQP.Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException
    {
        return delegate.queueUnbind(queue, exchange, routingKey, arguments);
    }

    @Override
    public AMQP.Queue.PurgeOk queuePurge(String queue) throws IOException
    {
        return delegate.queuePurge(queue);
    }

    @Override
    public GetResponse basicGet(String queue, boolean autoAck) throws IOException
    {
        return delegate.basicGet(queue, autoAck);
    }

    @Override
    public void basicAck(long deliveryTag, boolean multiple) throws IOException
    {
        delegate.basicAck(deliveryTag, multiple);
    }

    @Override
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException
    {
        delegate.basicNack(deliveryTag, multiple, requeue);
    }

    @Override
    public void basicReject(long deliveryTag, boolean requeue) throws IOException
    {
        delegate.basicReject(deliveryTag, requeue);
    }

    private void removeListeners()
    {
        delegate.removeShutdownListener(shutdownListener);
        delegate.removeReturnListener(returnListener);
        delegate.removeConfirmListener(confirmListener);
    }

    private BasicProperties addSequenceNumberHeader(BasicProperties props, long seqNo)
    {
        Map<String, Object> headers = props.getHeaders();
        if (headers == null)
        {
            headers = new HashMap<>();
        }
        else
        {
            headers = new HashMap<>(headers);
        }
        headers.put(SEQUENCE_NUMBER_HEADER, seqNo);
        return props.builder().headers(headers).build();
    }

    private void handleAck(long seqNo, boolean multiple)
    {
        handleAckNack(seqNo, multiple, false);
    }

    private void handleNack(long seqNo, boolean multiple)
    {
        handleAckNack(seqNo, multiple, true);
    }

    private void handleAckNack(long seqNo, boolean multiple, boolean nack)
    {
        if (multiple)
        {
            for (Long seq : new ArrayList<>(confirmations.keySet()))
            {
                if (seq <= seqNo)
                {
                    ConfirmationEntry<?> entry = confirmations.remove(seq);
                    if (entry != null)
                    {
                        if (nack)
                        {
                            entry.completeExceptionally(seq);
                        }
                        else
                        {
                            entry.complete();
                        }
                        entry.releasePermit();
                    }
                }
            }
        }
        else
        {
            ConfirmationEntry<?> entry = confirmations.remove(seqNo);
            if (entry != null)
            {
                if (nack)
                {
                    entry.completeExceptionally(seqNo);
                }
                else
                {
                    entry.complete();
                }
                entry.releasePermit();
            }
        }
    }

    private void handleReturn(int replyCode, String replyText, String exchange,
                               String routingKey, BasicProperties props, byte[] body)
    {
        Object seqNumObj = props.getHeaders().get(SEQUENCE_NUMBER_HEADER);
        long seqNo = extractSequenceNumber(seqNumObj);

        ConfirmationEntry<?> entry = confirmations.remove(seqNo);
        if (entry != null)
        {
            entry.completeExceptionally(new PublishException(seqNo, true,
                    exchange, routingKey, replyCode, replyText, entry.context));
            entry.releasePermit();
        }
    }

    private void handleShutdown(ShutdownSignalException cause)
    {
        AlreadyClosedException ex = new AlreadyClosedException(cause);
        for (ConfirmationEntry<?> entry : confirmations.values())
        {
            entry.completeExceptionally(ex);
            entry.releasePermit();
        }
        confirmations.clear();
    }

    /**
     * Extract sequence number from message header.
     * NOTE: Since this library always writes the sequence number as a Long,
     * the header value should always be a Long when read back from the broker.
     * The additional type checks (Integer, String, byte[]) are defensive programming
     * and should never be needed in practice.
     */
    private long extractSequenceNumber(Object seqNumObj)
    {
        if (seqNumObj instanceof Long)
        {
            return (Long) seqNumObj;
        }
        else if (seqNumObj instanceof Integer)
        {
            return ((Integer) seqNumObj).longValue();
        }
        else if (seqNumObj instanceof String)
        {
            return Long.parseLong((String) seqNumObj);
        }
        else if (seqNumObj instanceof byte[])
        {
            return Long.parseLong(new String((byte[]) seqNumObj));
        }
        return 0;
    }

    private static class ConfirmationEntry<T>
    {
        final CompletableFuture<T> future;
        final RateLimiter.Permit permit;
        final T context;

        ConfirmationEntry(CompletableFuture<T> future, RateLimiter.Permit permit, T context)
        {
            if (future == null)
            {
                throw new IllegalArgumentException("future must be non-null");
            }
            this.future = future;
            this.permit = permit;
            this.context = context;
        }

        void complete()
        {
            future.complete(context);
        }

        void completeExceptionally(long seq)
        {
            PublishException ex = new PublishException(seq, this.context);
            future.completeExceptionally(ex);
        }

        void completeExceptionally(Exception e)
        {
            future.completeExceptionally(e);
        }

        void releasePermit()
        {
            if (permit != null)
            {
                permit.release();
            }
        }
    }

}
