//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2013 GoPivotal, Inc.  All rights reserved.
//


package com.rabbitmq.client;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.utility.SensibleClone;
import com.rabbitmq.utility.Utility;

/**
 * Convenience class: an implementation of {@link Consumer} with
 * straightforward blocking semantics.
 *
 * The general pattern for using QueueingConsumer is as follows:
 *
 * <pre>
 * // Create connection and channel.
 * {@link ConnectionFactory} factory = new ConnectionFactory();
 * Connection conn = factory.newConnection();
 * {@link Channel} ch1 = conn.createChannel();
 *
 * // Declare a queue and bind it to an exchange.
 * String queueName = ch1.queueDeclare().{@link AMQP.Queue.DeclareOk#getQueue getQueue}();
 * ch1.{@link Channel#queueBind queueBind}(queueName, exchangeName, queueName);
 *
 * // Create the QueueingConsumer and have it consume from the queue
 * QueueingConsumer consumer = new {@link QueueingConsumer#QueueingConsumer(Channel) QueueingConsumer}(ch1);
 * ch1.{@link Channel#basicConsume basicConsume}(queueName, false, consumer);
 *
 * // Process deliveries
 * while (/* some condition * /) {
 *     {@link QueueingConsumer.Delivery} delivery = consumer.{@link QueueingConsumer#nextDelivery nextDelivery}();
 *     // process delivery
 *     ch1.{@link Channel#basicAck basicAck}(delivery.{@link QueueingConsumer.Delivery#getEnvelope getEnvelope}().{@link Envelope#getDeliveryTag getDeliveryTag}(), false);
 * }
 * </pre>
 *
 *
 * <p>For a more complete example, see LogTail in the <code>test/src/com/rabbitmq/examples</code>
 * directory of the source distribution.</p>
 * <p/>
 * <i><code>QueueingConsumer</code> was introduced to allow
 * applications to overcome a limitation in the way <code>Connection</code>
 * managed threads and consumer dispatching. When <code>QueueingConsumer</code>
 * was introduced, callbacks to <code>Consumers</code> were made on the
 * <code>Connection's</code> thread. This had two main drawbacks. Firstly, the
 * <code>Consumer</code> could stall the processing of all
 * <code>Channels</code> on the <code>Connection</code>. Secondly, if a
 * <code>Consumer</code> made a recursive synchronous call into its
 * <code>Channel</code> the client would deadlock.
 * <p/>
 * <code>QueueingConsumer</code> provided client code with an easy way to
 * obviate this problem by queueing incoming messages and processing them on
 * a separate, application-managed thread.
 * <p/>
 * The threading behaviour of <code>Connection</code> and <code>Channel</code>
 * has been changed so that each <code>Channel</code> uses a distinct thread
 * for dispatching to <code>Consumers</code>. This prevents
 * <code>Consumers</code> on one <code>Channel</code> holding up
 * <code>Consumers</code> on another and it also prevents recursive calls from
 * deadlocking the client.
 * <p/>
 * As such, it is now safe to implement <code>Consumer</code> directly or
 * to extend <code>DefaultConsumer</code>.</i>
 * <p>
 * <code>QueueingConsumer</code> still offers a simple API for consuming from
 * one or more queues. Note that when consuming from more than one queue and
 * calling <code>nextDelivery()</code>, cancellation and shutdown signals will
 * not be thrown in the caller's thread unless/until <em>all</em> consumers
 * have been cancelled or, in the case of a shutdown signal, have all observed
 * the signal. See <code>nextDelivery()</code> for more details.
 * </p>
 */
public class QueueingConsumer extends DefaultConsumer {
    private final BlockingQueue<Delivery> _queue;
    private final Set<String> consumerTags;

    public QueueingConsumer(Channel ch) {
        this(ch, new LinkedBlockingDeque<Delivery>());
    }

    public QueueingConsumer(Channel ch, BlockingDeque<Delivery> q) {
        super(ch);
        this._queue = q;
        this.consumerTags = new ConcurrentSkipListSet<String>();
    }

    @Override public void handleConsumeOk(String consumerTag) {
        consumerTags.add(consumerTag);
        super.handleConsumeOk(consumerTag);
    }

    @Override public void handleCancelOk(String consumerTag) {
        consumerTags.remove(consumerTag);
        super.handleCancelOk(consumerTag);
    }

    @Override public void handleShutdownSignal(String consumerTag,
                                               ShutdownSignalException sig) {
        consumerTags.remove(consumerTag);
        if (consumerTags.isEmpty()) {
            _queue.add(new Delivery.ShutdownSignalPoison(sig));
        }
    }

    @Override public void handleCancel(String consumerTag) throws IOException {
        consumerTags.remove(consumerTag);
        if (consumerTags.isEmpty()) {
            _queue.add(new Delivery.ConsumerCancelledPoison());
        }
    }

    @Override public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body)
        throws IOException
    {
        this._queue.add(new Delivery(consumerTag, envelope, properties, body));
    }

    /**
     * Encapsulates an arbitrary message - simple "bean" holder structure.
     */
    public static class Delivery {
        private final String _consumerTag;
        private final Envelope _envelope;
        private final AMQP.BasicProperties _properties;
        private final byte[] _body;

        public Delivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
            this._consumerTag = consumerTag;
            this._envelope = envelope;
            this._properties = properties;
            this._body = body;
        }

        /**
         * Retrieve the message envelope.
         * @return the message envelope
         */
        public Envelope getEnvelope() {
            return _envelope;
        }

        /**
         * Retrieve the message properties.
         * @return the message properties
         */
        public BasicProperties getProperties() {
            return _properties;
        }

        /**
         * Retrieve the message body.
         * @return the message body
         */
        public byte[] getBody() {
            return _body;
        }

        /**
         * Retrieve the consumer tag for this delivery.
         * @return the consumer tag
         */
        public String getConsumerTag() {
            return _consumerTag;
        }

        void throwIfNecessary() { }

        boolean needsReQueue() { return false; }

        static class Poison<T extends RuntimeException & SensibleClone<T>> extends Delivery {
            private final T exception;
            public Poison(final T exception) {
                super(null, null, null, null);
                this.exception = exception;
            }

            @Override
            void throwIfNecessary() {
                throw Utility.fixStackTrace(exception);
            }
        }

        static class ConsumerCancelledPoison extends Poison<ConsumerCancelledException> {
            public ConsumerCancelledPoison() {
                super(new ConsumerCancelledException());
            }

            @Override
            boolean needsReQueue() {
                return false;
            }
        }

        static class ShutdownSignalPoison extends Poison<ShutdownSignalException> {
            public ShutdownSignalPoison(ShutdownSignalException ex) {
                super(ex);
            }

            @Override
            boolean needsReQueue() {
                return true;
            }
        }
    }

    /**
     * Check if we are in shutdown mode and if so throw an exception.
     */
    private void checkShutdown() {
        if (_shutdown != null)
            throw Utility.fixStackTrace(_shutdown);
    }

    private Delivery handle(final Delivery delivery) throws ConsumerCancelledException,
                                                      ShutdownSignalException {
        // If delivery is null, it is a timeout and we have no poison pill
        // in the queue, so returning null is the appropriate thing to do.
        if (delivery == null) return null;

        if (delivery.needsReQueue()) {
            queue.addLast(delivery);
        }

        delivery.throwIfNecessary();
        return delivery;
    }

    /**
     * Main application-side API: wait for the next message delivery and return it.
     * @return the next message
     * @throws InterruptedException if an interrupt is received while waiting
     * @throws ShutdownSignalException if the connection is shut down while waiting
     * @throws ConsumerCancelledException if this consumer is cancelled while waiting
     */
    public Delivery nextDelivery()
        throws InterruptedException, ShutdownSignalException, ConsumerCancelledException
    {
        return handle(_queue.take());
    }

    /**
     * Main application-side API: wait for the next message delivery and return it.
     * @param timeout timeout in millisecond
     * @return the next message or null if timed out
     * @throws InterruptedException if an interrupt is received while waiting
     * @throws ShutdownSignalException if the connection is shut down while waiting
     * @throws ConsumerCancelledException if this consumer is cancelled while waiting
     */
    public Delivery nextDelivery(long timeout)
        throws InterruptedException, ShutdownSignalException, ConsumerCancelledException
    {
        return handle(_queue.poll(timeout, TimeUnit.MILLISECONDS));
    }
}
