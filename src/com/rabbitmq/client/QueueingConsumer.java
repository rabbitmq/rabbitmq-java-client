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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
 * <p>Note that a <code>QueueingConsumer</code> will raise a channel error if you attempt to
 * start multiple consumers with it.</p>
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
 */
public class QueueingConsumer extends DefaultConsumer {

    private final BlockingQueue<Delivery> queue;

    // When this is non-null the queue is in shutdown mode and nextDelivery should
    // throw a shutdown signal exception.
    private volatile ShutdownSignalException shutdown;
    private volatile ConsumerCancelledException cancelled;
    private volatile MultipleQueueingConsumersException duplicate;

    // Marker object used to signal the queue is in shutdown mode.
    // It is only there to wake up consumers. The canonical representation
    // of shutting down is the presence of shutdown.
    // Invariant: This is never on queue unless shutdown != null.
    private static final Delivery POISON = new Delivery(null, null, null);

    public QueueingConsumer(Channel ch) {
        this(ch, new LinkedBlockingQueue<Delivery>());
    }

    public QueueingConsumer(Channel ch, BlockingQueue<Delivery> q) {
        super(ch);
        this.queue = q;
    }
    
    /**
     * Stores the most recently passed-in consumerTag - semantically, there should be only one.
     * @see Consumer#handleConsumeOk
     */
    public void handleConsumeOk(String consumerTag) {
        final String originalConsumerTag = getConsumerTag();
        if (originalConsumerTag != null) {
            duplicate = new MultipleQueueingConsumersException(originalConsumerTag, consumerTag);
            queue.add(POISON);
            throw duplicate;
        }
        super.handleConsumeOk(consumerTag);
    }
    
    @Override public void handleShutdownSignal(String consumerTag,
                                               ShutdownSignalException sig) {
        shutdown = sig;
        queue.add(POISON);
    }

    @Override public void handleCancel(String consumerTag) throws IOException {
        cancelled = new ConsumerCancelledException();
        queue.add(POISON);
    }

    @Override public void handleDelivery(String consumerTag,
                                         Envelope envelope,
                                         AMQP.BasicProperties properties,
                                         byte[] body)
        throws IOException
    {
        if (!consumerTag.equals(this.getConsumerTag())) {
            throw new IllegalStateException(
                    String.format(
                            "Got delivery with consumer tag %s, when %s was expected: ",
                            consumerTag, this.getConsumerTag()));
        }
        checkShutdown();
        this.queue.add(new Delivery(envelope, properties, body));
    }

    /**
     * Thrown when a queueing consumer detects a consume.ok method
     * after its consumer tag has already been set.
     *
     */
    public static class MultipleQueueingConsumersException extends RuntimeException implements
            SensibleClone<MultipleQueueingConsumersException> {

        /** Default for non-checking. */
        private static final long serialVersionUID = 1L;
        
        private final String initialConsumerTag;
        private final String receivedConsumerTag;

        public MultipleQueueingConsumersException(String initialConsumerTag,
                                                  String receivedConsumerTag) {
            super("Unsupported multiple consumers detected by QueueingConsumer" +
                  "; initial consumer tag: " + initialConsumerTag +
                  "; received consumer tag: " + receivedConsumerTag);
            this.initialConsumerTag = initialConsumerTag;
            this.receivedConsumerTag = receivedConsumerTag;
        }
        

        public MultipleQueueingConsumersException sensibleClone() {
            try {
                return (MultipleQueueingConsumersException) super.clone();
            } catch (CloneNotSupportedException e) {
                // You've got to be kidding me
                throw new Error(e);
            }
        }
        
        public String getInitialConsumerTag() {
            return initialConsumerTag;
        }

        public String getReceivedConsumerTag() {
            return receivedConsumerTag;
        }

    }

    /**
     * Encapsulates an arbitrary message - simple "bean" holder structure.
     */
    public static class Delivery {
        private final Envelope envelope;
        private final AMQP.BasicProperties properties;
        private final byte[] body;

        public Delivery(Envelope envelope, BasicProperties properties, byte[] body) {
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

    }

    /**
     * Check if we are in shutdown mode and if so throw an exception.
     */
    private void checkShutdown() {
        if (shutdown != null)
            throw Utility.fixStackTrace(shutdown);
    }

    /**
     * If delivery is not POISON nor null, return it.
     * <p/>
     * If delivery, shutdown and cancelled are all null, return null.
     * <p/>
     * If delivery is POISON re-insert POISON into the queue and
     * throw an exception if POISONed for no reason.
     * <p/>
     * Otherwise, if we are in shutdown mode or cancelled,
     * throw a corresponding exception.
     */
    private Delivery handle(Delivery delivery) {
        if (delivery == POISON ||
            delivery == null && (duplicate != null || shutdown != null || cancelled != null)) {
            if (delivery == POISON) {
                queue.add(POISON);
                if (shutdown == null && cancelled == null && duplicate == null) {
                    throw new IllegalStateException(
                        "POISON in queue, but null shutdown, null cancelled and null duplicate. " +
                        "This should never happen, please report as a BUG");
                }
            }
            if (null != duplicate)
                throw Utility.fixStackTrace(duplicate);
            if (null != shutdown)
                throw Utility.fixStackTrace(shutdown);
            if (null != cancelled)
                throw Utility.fixStackTrace(cancelled);
        }
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
        throws InterruptedException, ShutdownSignalException, ConsumerCancelledException
    {
        return handle(queue.poll(timeout, TimeUnit.MILLISECONDS));
    }
}
