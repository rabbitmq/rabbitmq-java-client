// The contents of this file are subject to the Mozilla Public License
// Version 1.1 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License
// at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS"
// basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
// the License for the specific language governing rights and
// limitations under the License.
//
// The Original Code is RabbitMQ.
//
// The Initial Developer of the Original Code is VMware, Inc.
// Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.facilities;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * A {@link RpcProcessor RpcProcessor&lt;byte[], byte[]&gt;} that receives requests as byte array
 * bodies of messages on a RabbitMQ queue, and sends responses on the <code>replyTo</code> queue in
 * the message header. A consumer listens on the queue and this processor ensures that the start and
 * stop functions correctly process messages on the queue.
 */
public class ByteArrayRpcProcessor implements RpcProcessor<byte[], byte[]> {

    private static final long STOP_TIMEOUT_SECONDS = 3L;
    /** Ever started */
    private volatile boolean started = false;
    /** Ever stopped */
    private volatile boolean stopped = false;

    /** Channel we are communicating on */
    private final Channel channel;
    /** Queue we receive requests from */
    private final String queueName;
    /** Latch for stopping */
    private final CountDownLatch stopLatch;

    /** Monitor/lock for consumer, */
    private final Object monitor = new Object();
    /** ConsumerTag for consumer attached to the request queue */
    private String consumerTag;

    /**
     * Create a basic processor receiving byte array requests on a Rabbit Queue on a given channel.
     * The channel and queue must already exist.
     *
     * @param channel through which to communicate with RabbitMQ
     * @param queueName of existing queue accessible on this <code>channel</code>
     * @throws IOException if the queue does not exist or the <code>channel</code> is not valid
     */
    public ByteArrayRpcProcessor(Channel channel, String queueName) throws IOException {
        this.channel = channel;
        this.queueName = channel.queueDeclarePassive(queueName).getQueue();
        this.stopLatch = new CountDownLatch(1);
    }

    public void start(RpcHandler<byte[], byte[]> rpcHandler) throws IOException {
        if (this.started)
            throw new IOException("Already started.");
        synchronized (this.monitor) {
            Consumer consumer = this.makeConsumer(rpcHandler);
            this.consumerTag = this.channel.basicConsume(this.queueName, consumer);
            this.started = true;
        }
    }

    public void stop() throws IOException {
        if (this.started) {
            if (!this.stopped) {
                synchronized (this.monitor) {
                    this.channel.basicCancel(this.consumerTag);
                    this.stopped = true;
                }
            }
            try {
                this.stopLatch.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException _) {/* do nothing */
            }
        }
    }

    private Consumer makeConsumer(RpcHandler<byte[], byte[]> rpcHandler) throws IOException {
        return new ByteArrayProcessorConsumer(this.channel, rpcHandler, this.stopLatch, true);
    }

    /**
     * A {@link Consumer} that passes the message bodies to an {@link RpcHandler RpcHandler&lt;byte[], byte[]&gt;}, replies with the
     * response if there is one expected and optionally acknowledges receipt of the message.
     */
    private static class ByteArrayProcessorConsumer implements Consumer {

        private final Channel channel;
        private final RpcHandler<byte[], byte[]> rpcHandler;
        private final CountDownLatch stopLatch;
        private final boolean autoAck;

        ByteArrayProcessorConsumer(Channel channel, RpcHandler<byte[], byte[]> rpcHandler,
                CountDownLatch stopLatch, boolean autoAck) {
            this.channel = channel;
            this.rpcHandler = rpcHandler;
            this.stopLatch = stopLatch;
            this.autoAck = autoAck;
        }

        public void handleConsumeOk(String consumerTag) {
        }

        public void handleCancelOk(String consumerTag) {
            this.stopLatch.countDown();
        }

        public void handleCancel(String consumerTag) throws IOException {
        }

        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        }

        public void handleRecoverOk(String consumerTag) {
        }

        public void handleDelivery(String consumerTag, Envelope envelope,
                BasicProperties properties, byte[] body) throws IOException {
            String correlationId = properties.getCorrelationId();
            String replyTo = properties.getReplyTo();
            if (correlationId != null && replyTo != null) {
                BasicProperties replyProperties = new BasicProperties.Builder().correlationId(
                        correlationId).build();
                byte[] replyBody = this.rpcHandler.handleCall(body);
                this.channel.basicPublish("", replyTo, replyProperties, replyBody);
            } else {
                this.rpcHandler.handleCast(body);
            }
            if (this.autoAck)
                this.channel.basicAck(envelope.getDeliveryTag(), false);
        }
    }
}
