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
import java.util.concurrent.*;

import com.rabbitmq.client.AMQP.BasicProperties;
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
 * <b>deprecated</b> <i><code>QueueingConsumer</code> was introduced to allow
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
 */
public class QueueingConsumer extends DefaultConsumer {

    private final BlockingQueue<Delivery> queue;
    private final Set<String> consumerTags;

    public QueueingConsumer(Channel ch) {
        this(ch, new LinkedBlockingQueue<Delivery>());
    }

    public QueueingConsumer(Channel ch, BlockingQueue<Delivery> q) {
        super(ch);
        this.queue = q;
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
            queue.add(new Delivery.ShutdownSignalPoison(sig));
        }
    }

    @Override public void handleCancel(String consumerTag) throws IOException {
        consumerTags.remove(consumerTag);
        if (consumerTags.isEmpty()) {
            queue.add(new Delivery.ConsumerCancelledPoison());
        }
    }

    private Delivery handle(Delivery delivery) throws ConsumerCancelledException,
                                                      ShutdownSignalException {
        // If delivery is null, it is a timeout and we have no poison pill
        // in the queue, so returning null is the appropriate thing to do.
        if (delivery != null && delivery.isPoison()) {
            // Poison needs to be re-added to the queue
            // TODO: this is currently a bug (of sorts) IMO - we add the poison
            // pill to the back of the queue, so other messages could interleave
            // and some (arbitrary) caller of #nextDelivery() will get the exception
            // in the future. I think instead, that we should either (a) not
            // re-queue the poison, so that the consumer can be re-used or
            // (b) put the poison into the head of the queue, but switching to
            // BlockingDequeue<T> and calling #addFirst() - my money is on
            // (b) offer better semantics, but I could be wrong.
            queue.add(delivery);
            delivery.throwIfNecessary();
        }
        return delivery;
    }

    @Override public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body)
        throws IOException
    {
        queue.add(new Delivery(consumerTag, envelope, properties, body));
    }

    /**
     * Encapsulates an arbitrary message - simple "bean" holder structure.
     */
    public static class Delivery {
        private final String consumerTag;
        private final Envelope envelope;
        private final AMQP.BasicProperties properties;
        private final byte[] body;

        public Delivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
            this.consumerTag = consumerTag;
            this.envelope = envelope;
            this.properties = properties;
            this.body = body;
        }

        /**
         * Retrieve the message envelope.
         * @return the message envelope
         */
        public Envelope getEnvelope() {
            return envelope;
        }

        /**
         * Retrieve the message properties.
         * @return the message properties
         */
        public BasicProperties getProperties() {
            return properties;
        }

        /**
         * Retrieve the message body.
         * @return the message body
         */
        public byte[] getBody() {
            return body;
        }

        /**
         * Retrieve the consumer tag for this delivery.
         * @return the consumer tag
         */
        public String getConsumerTag() {
            return consumerTag;
        }

        boolean isPoison() { return false; }

        void throwIfNecessary() { }

        static class ConsumerCancelledPoison extends Delivery {
            public ConsumerCancelledPoison() {
                super(null, null, null, null);
            }

            @Override
            boolean isPoison() { return true; }

            @Override
            void throwIfNecessary() {
                throw Utility.fixStackTrace(new ConsumerCancelledException());
            }
        }

        static class ShutdownSignalPoison extends Delivery {
            private final ShutdownSignalException exception;

            public ShutdownSignalPoison(ShutdownSignalException ex) {
                super(null, null, null, null);
                this.exception = ex;
            }

            @Override
            boolean isPoison() { return true; }

            @Override
            void throwIfNecessary() {
                throw Utility.fixStackTrace(exception);
            }
        }
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
        return handle(queue.take());
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
            throws InterruptedException, ConsumerCancelledException, ShutdownSignalException
    {
        return handle(queue.poll(timeout, TimeUnit.MILLISECONDS));
    }

}
