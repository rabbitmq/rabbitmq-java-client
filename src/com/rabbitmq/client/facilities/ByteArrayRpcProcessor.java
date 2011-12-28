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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * A {@link RpcProcessor RpcProcessor&lt;byte[], byte[]&gt;} that receives requests as byte array
 * bodies of messages on a RabbitMQ queue, and sends responses on the <code>replyTo</code> queue in
 * the message header. A consumer listens on the queue and this processor ensures that the start and
 * stop functions correctly process messages on the queue.
 */
public class ByteArrayRpcProcessor implements RpcProcessor<byte[], byte[]> {

    /** Ever started */
    private volatile boolean started = false;

    /** Channel we are communicating on */
    private final Channel channel;
    /** Queue we receive requests from */
    private final String queueName;

    /** Monitor/lock for consumer, */
    private final Object monitor = new Object();
    /** Consumer attached to the request queue */
    private Consumer consumer;

    /**
     * Create a basic processor receiving byte array requests on a Rabbit Queue on a given channel.
     * The channel and queue must already exist.
     * @param channel through which to communicate with RabbitMQ
     * @param queueName of existing queue accessible on this <code>channel</code>
     * @throws IOException if the queue does not exist or the <code>channel</code> is not valid
     */
    public ByteArrayRpcProcessor(Channel channel, String queueName)
            throws IOException {
        this.channel = channel;
        this.queueName = channel.queueDeclarePassive(queueName).getQueue();
    }

    public void start(RpcHandler<byte[], byte[]> rpcHandler) throws IOException {
        if (this.started) throw new IOException("Already started.");
        synchronized (this.monitor) {
            this.consumer = this.makeConsumer(rpcHandler, this.channel);
        }

    }

    public void stop() throws IOException {
        // TODO Auto-generated method stub

    }

    /**
     * If the passed-in queue name is null, creates a server-named temporary exclusive autodelete
     * queue to use; otherwise expects the queue to have already been declared.
     */
    public RpcServer(Channel channel, String queueName) throws IOException {
        _channel = channel;
        if (queueName == null || queueName.equals("")) {
            _queueName = channel.queueDeclare().getQueue();
        } else {
            _queueName = queueName;
        }
        _consumer = setupConsumer();
    }

    /**
     * Public API - cancels the consumer, thus deleting the queue, if it was a temporary queue, and
     * marks the RpcServer as closed.
     * @throws IOException if an error is encountered
     */
    public void close() throws IOException {
        if (_consumer != null) {
            _channel.basicCancel(_consumer.getConsumerTag());
            _consumer = null;
        }
        terminateMainloop();
    }

    /**
     * Registers our consumer on the request queue.
     * @throws IOException if an error is encountered
     * @return the newly created and registered consumer
     */
    private Consumer makeConsumer(RpcHandler<byte[], byte[]> rpcHandler)
            throws IOException {
        Consumer consumer = new ByteArrayProcessorConsumer(this.channel,
                rpcHandler);
        this.channel.basicConsume(this.queueName, consumer);
        return consumer;
    }

    private static class ByteArrayProcessorConsumer implements Consumer {

        private final Channel channel;
        private final RpcHandler<byte[], byte[]> rpcHandler;

        ByteArrayProcessorConsumer(Channel channel,
                RpcHandler<byte[], byte[]> rpcHandler) {
            this.channel = channel;
            this.rpcHandler = rpcHandler;
        }

        public void handleConsumeOk(String consumerTag) {
            // TODO called by basicConsume()
        }

        public void handleCancelOk(String consumerTag) {
            // TODO we got cancelled explicitly
        }

        public void handleCancel(String consumerTag) throws IOException {
            // TODO handle queue-deletion before cancel
        }

        public void handleShutdownSignal(String consumerTag,
                ShutdownSignalException sig) {
            // TODO the channel or connection went down
        }

        public void handleRecoverOk(String consumerTag) {
            // TODO Auto-generated method stub
        }

        public void handleDelivery(String consumerTag, Envelope envelope,
                BasicProperties properties, byte[] body) throws IOException {
            String correlationId = properties.getCorrelationId();
            String replyTo = properties.getReplyTo();
            if (correlationId != null && replyTo != null) {
                BasicProperties replyProperties = new BasicProperties.Builder()
                        .correlationId(correlationId).build();
                byte[] replyBody = this.rpcHandler.handleCall(body);
                this.channel.basicPublish("", replyTo, replyProperties,
                        replyBody);
            } else {
                this.rpcHandler.handleCast(body);
            }
        }
    }

    /**
     * Public API - main server loop. Call this to begin processing requests. Request processing
     * will continue until the Channel (or its underlying Connection) is shut down, or until
     * terminateMainloop() is called. Note that if the mainloop is blocked waiting for a request,
     * the termination flag is not checked until a request is received, so a good time to call
     * terminateMainloop() is during a request handler.
     * @return the exception that signalled the Channel shutdown, or null for orderly shutdown
     */
    public ShutdownSignalException mainloop() throws IOException {
        try {
            while (_mainloopRunning) {
                QueueingConsumer.Delivery request;
                try {
                    request = _consumer.nextDelivery();
                } catch (InterruptedException ie) {
                    continue;
                }
                processRequest(request);
                _channel.basicAck(request.getEnvelope().getDeliveryTag(), false);
            }
            return null;
        } catch (ShutdownSignalException sse) {
            return sse;
        }
    }

    /**
     * Call this method to terminate the mainloop. Note that if the mainloop is blocked waiting for
     * a request, the termination flag is not checked until a request is received, so a good time to
     * call terminateMainloop() is during a request handler.
     */
    public void terminateMainloop() {
        _mainloopRunning = false;
    }

    /**
     * Private API - Process a single request. Called from mainloop().
     */
    public void processRequest(QueueingConsumer.Delivery request)
            throws IOException {
        AMQP.BasicProperties requestProperties = request.getProperties();
        String correlationId = requestProperties.getCorrelationId();
        String replyTo = requestProperties.getReplyTo();
        if (correlationId != null && replyTo != null) {
            AMQP.BasicProperties replyProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId).build();
            byte[] replyBody = handleCall(request, replyProperties);
            _channel.basicPublish("", replyTo, replyProperties, replyBody);
        } else {
            handleCast(request);
        }
    }

    /**
     * Lowest-level response method. Calls
     * handleCall(AMQP.BasicProperties,byte[],AMQP.BasicProperties).
     */
    public byte[] handleCall(QueueingConsumer.Delivery request,
            AMQP.BasicProperties replyProperties) {
        return handleCall(request.getProperties(), request.getBody(),
                replyProperties);
    }

    /**
     * Mid-level response method. Calls handleCall(byte[],AMQP.BasicProperties).
     */
    public byte[] handleCall(AMQP.BasicProperties requestProperties,
            byte[] requestBody, AMQP.BasicProperties replyProperties) {
        return handleCall(requestBody, replyProperties);
    }

    /**
     * High-level response method. Returns an empty response by default - override this (or other
     * handleCall and handleCast methods) in subclasses.
     */
    public byte[] handleCall(byte[] requestBody,
            AMQP.BasicProperties replyProperties) {
        return new byte[0];
    }

    /**
     * Lowest-level handler method. Calls handleCast(AMQP.BasicProperties,byte[]).
     */
    public void handleCast(QueueingConsumer.Delivery request) {
        handleCast(request.getProperties(), request.getBody());
    }

    /**
     * Mid-level handler method. Calls handleCast(byte[]).
     */
    public void handleCast(AMQP.BasicProperties requestProperties,
            byte[] requestBody) {
        handleCast(requestBody);
    }

    /**
     * High-level handler method. Does nothing by default - override this (or other handleCast and
     * handleCast methods) in subclasses.
     */
    public void handleCast(byte[] requestBody) {
        // Does nothing.
    }

    /**
     * Retrieve the channel.
     * @return the channel to which this server is connected
     */
    public Channel getChannel() {
        return _channel;
    }

    /**
     * Retrieve the queue name.
     * @return the queue which this server is consuming from
     */
    public String getQueueName() {
        return _queueName;
    }

}
