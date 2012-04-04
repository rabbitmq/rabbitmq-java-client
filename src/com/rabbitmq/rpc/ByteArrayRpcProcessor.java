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

package com.rabbitmq.rpc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * A {@link RpcProcessor RpcProcessor&lt;byte[], byte[]&gt;} that receives requests as byte array
 * bodies of messages on a RabbitMQ queue. A consumer listens on the queue and this processor
 * ensures that the start and stop functions correctly process messages on the queue. Responses are
 * sent to the <code>replyTo</code> queue in the message header.
 * <p/>
 * This processor is serially reusable; that is, after being {@link #stop}ped it may be
 * {@link #start}ed again, potentially on a different channel.
 */
public class ByteArrayRpcProcessor implements RpcProcessor {

    private static final long STOP_TIMEOUT_SECONDS = 3L;
    /** Currently started */
    private volatile boolean started = false;
    /** Currently stopped */
    private volatile boolean stopped = true;

    /** Queue we are listening on */
    private final String queueName;
    /** Handler for each request */
    private final RpcHandler<byte[], byte[]> rpcHandler;
    /** Whether acknowledgements are automatic or we do them after processing. */
    private final boolean autoAck;

    /** Monitor/lock for <code>consumerTag</code>, <code>channel</code>, <code>stopLatch</code> */
    private final Object monitor = new Object();
    /** Channel we are communicating on */
    private Channel channel;
    /** ConsumerTag for consumer attached to the request queue */
    private String consumerTag;
    /** Latch for stopping */
    private CountDownLatch stopLatch;

    /**
     * Create a basic processor to receive byte array requests from a Rabbit Queue.
     * @param queueName of queue from which to receive requests
     * @param autoAck set to true if acknowledgements are automatic on receipt, or false if they are
     *            to be explicit after processing
     * @param rpcHandler used to process each request
     */
    public ByteArrayRpcProcessor(String queueName, boolean autoAck,
            RpcHandler<byte[], byte[]> rpcHandler) {
        if (rpcHandler == null)
            throw new NullPointerException("RpcHandler cannot be null");
        this.rpcHandler = rpcHandler;
        this.queueName = queueName;
        this.autoAck = autoAck;
    }

    public void start(Channel channel) throws IOException {
        if (this.started) throw new IOException("Already started.");
        synchronized (this.monitor) {
            this.channel = channel;
            channel.queueDeclarePassive(queueName).getQueue();
            Consumer consumer = this.makeConsumer(this.rpcHandler);
            this.consumerTag = channel.basicConsume(this.queueName,
                    this.autoAck, consumer);
            this.started = true;
        }
    }

    public void stop() throws IOException {
        if (!this.started) throw new IOException("Not started.");
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
        this.started = false;
    }

    private Consumer makeConsumer(RpcHandler<byte[], byte[]> rpcHandler)
            throws IOException {
        this.stopLatch = new CountDownLatch(1);
        return new ByteArrayProcessorConsumer(this.channel, rpcHandler,
                this.stopLatch, this.autoAck);
    }

    /**
     * A {@link Consumer} that passes the message bodies to an {@link RpcHandler
     * RpcHandler&lt;byte[], byte[]&gt;}, replies with the response if there is one expected and
     * acknowledges receipt of the message if not automatic.
     */
    private static class ByteArrayProcessorConsumer implements Consumer {

        private final Channel channel;
        private final RpcHandler<byte[], byte[]> rpcHandler;
        private final CountDownLatch stopLatch;
        private final boolean autoAck;

        ByteArrayProcessorConsumer(Channel channel,
                RpcHandler<byte[], byte[]> rpcHandler,
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

        public void handleShutdownSignal(String consumerTag,
                ShutdownSignalException sig) {
        }

        public void handleRecoverOk(String consumerTag) {
        }

        public void handleDelivery(String consumerTag, Envelope envelope,
                BasicProperties properties, byte[] body) throws IOException {
            String correlationId = properties.getCorrelationId();
            String replyTo = properties.getReplyTo();
            if (correlationId != null && replyTo != null) {
                BasicProperties replyProperties = new BasicProperties.Builder()
                        .correlationId(correlationId).build();
                byte[] replyBody = wrapHandleCall(envelope,
                        properties, body, replyProperties);
                this.channel.basicPublish("", replyTo, replyProperties,
                        replyBody);
            } else {
                this.rpcHandler.handleCast(envelope, properties, body);
            }
            if (!this.autoAck)
                this.channel.basicAck(envelope.getDeliveryTag(), false);
        }

        private byte[] wrapHandleCall(Envelope envelope, BasicProperties requestProps, byte[] body, BasicProperties replyProps) {
            try {
                byte[] replyBody = this.rpcHandler.handleCall(envelope,
                        requestProps, body, replyProps);
                return replyBody;
            } catch (RpcException re) {
                // Rpc infrastructure error, e.g. conversion error
                setExceptionReplyProps(replyProps, re);
                return exceptionReplyBody(re);
            } catch (Throwable t) {
                setExceptionReplyProps(replyProps, ServiceException.newServiceException(t.getMessage(), t));
                return exceptionReplyBody(t);
            }
        }

        private static void setExceptionReplyProps(BasicProperties replyProps, Throwable t) {
            String exceptionTypeName = t.getClass().getSimpleName();
            Map<String, Object> headers = replyProps.getHeaders();
            if (headers==null) headers = new HashMap<String, Object>();
            headers.put(RpcException.RPC_EXCEPTION_HEADER, exceptionTypeName);
            //TODO: change so that we don't have to use this deprecated interface
            replyProps.setHeaders(headers);
        }

        private static byte[] exceptionReplyBody(Throwable t) {
            byte[] body = null;
            try {
                body = t.getMessage().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                body = "<message text not encoded>".getBytes();
            }
            return body;
        }
    }
}
