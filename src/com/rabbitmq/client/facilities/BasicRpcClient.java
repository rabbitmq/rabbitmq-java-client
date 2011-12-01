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
// Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.facilities;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import com.rabbitmq.utility.BlockingCell;

/**
 * A class that sends Remote Procedure Call (Rpc) requests on a (fixed)
 * {@link Channel} and waits for the replies. Can send requests to multiple
 * servers concurrently. The server (to which the request is sent) is identified
 * by an exchange and routing-key, supplied on each request. Although each
 * thread that sends a request blocks until the reply is received, multiple
 * threads can send a request without holding each other up. Responses are
 * subject to a (fixed) timeout period, after which the response is deemed to be
 * null.
 * <p/>
 * This class manages a single queue used as a reply-to queue on all the
 * requests. The queue persists until the client is closed.
 * <p/>
 * In this basic class requests (and responses) are byte sequences.
 * <p/>
 * <b>Concurrent Semantics</b><br/>
 * This class is thread-safe. Multiple calls may be issued on multiple threads
 * without blocking each other.
 */
public class BasicRpcClient {
    /**
     * NO_TIMEOUT value must match convention on
     * {@link BlockingCell#uninterruptibleGet(int)}
     */
    public final static int NO_TIMEOUT = -1;

    /** Channel we communicate on */
    private final Channel channel;
    /** timeout milliseconds to wait for responses */
    private final int timeout;

    /** The name of our private reply queue */
    private final String replyQueue;

    /** Consumer attached to our reply queue */
    private final Consumer consumer;
    private final String consumerTag;

    /** Map from request correlation ID to continuation BlockingCell */
    private final Map<String, BlockingCell<Object>> continuationMap = new HashMap<String, BlockingCell<Object>>();
    /** Contains the most recently-used request correlation ID */
    private int correlationId;

    /**
     * Construct a new Client that will send requests on the given channel, waiting for responses.
     *
     * @param channel the channel to use for communication
     * @param timeout time (ms) to allow for response
     * @throws IOException if an error is encountered
     */
    public BasicRpcClient(Channel channel, int timeout) throws IOException {
        this.channel = channel;
        if (timeout < NO_TIMEOUT)
            throw new IllegalArgumentException(
                    "Timeout argument must be NO_TIMEOUT(-1) or non-negative.");
        this.timeout = timeout;
        this.correlationId = 0;

        this.replyQueue = setupReplyQueue(channel);
        this.consumer = createConsumer(channel, this.continuationMap);
        this.consumerTag = channel.basicConsume(this.replyQueue, true, this.consumer);
    }

    /**
     * Construct a new RpcClient that will communicate on the given channel,
     * sending requests to the given exchange with the given routing key.
     * <p/>
     * Causes the creation of a temporary private autodelete queue.
     * <p/>
     * Waits forever for responses (that is, no timeout).
     *
     * @param channel the channel to use for communication
     * @throws IOException if an error is encountered
     * @see #setupReplyQueue
     */
    public BasicRpcClient(Channel channel)
            throws IOException {
        this(channel, NO_TIMEOUT);
    }

    /**
     * Private API - ensures the RpcClient is correctly open.
     *
     * @throws IOException if an error is encountered
     */
    private void checkConsumer() throws IOException {
        if (this.consumer == null) {
            throw new EOFException("RpcClient is closed");
        }
    }

    /**
     * Public API - cancels the consumer, thus deleting the temporary queue, and
     * marks the RpcClient as closed.
     *
     * @throws IOException if an error is encountered
     */
    public void close() throws IOException {
        if (this.consumer != null) {
            this.channel.basicCancel(this.consumerTag);
        }
    }

    private static String setupReplyQueue(Channel channel) throws IOException {
        return channel.queueDeclare("", false, true, true, null).getQueue();
    }

    /**
     * Registers a consumer on the reply queue.
     *
     * @throws IOException if an error is encountered
     * @return the newly created and registered consumer
     */
    private static Consumer createConsumer(Channel channel, final Map<String, BlockingCell<Object>> continuationMap) throws IOException {
        return new DefaultConsumer(channel) {
            @Override
            public void handleShutdownSignal(String consumerTag,
                    ShutdownSignalException signal) {
                synchronized (continuationMap) {
                    for (Entry<String, BlockingCell<Object>> entry : continuationMap
                            .entrySet()) {
                        entry.getValue().set(signal);
                    }
                }
            }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                    AMQP.BasicProperties properties, byte[] body)
                    throws IOException {
                synchronized (continuationMap) {
                    String replyId = properties.getCorrelationId();
                    BlockingCell<Object> blocker = continuationMap
                            .get(replyId);
                    continuationMap.remove(replyId);
                    blocker.set(body);
                }
            }
        };
    }

    private void publish(String exchange, String routingKey, AMQP.BasicProperties props, byte[] message)
            throws IOException {
        this.channel.basicPublish(exchange, routingKey, props, message);
    }

    private byte[] primitiveCall(String exchange, String routingKey, AMQP.BasicProperties props, byte[] message)
            throws IOException, ShutdownSignalException, TimeoutException {
        checkConsumer();
        BlockingCell<Object> k = new BlockingCell<Object>();
        synchronized (this.continuationMap) {
            this.correlationId++;
            String replyId = "" + this.correlationId;
            props = ((props == null) ? new AMQP.BasicProperties.Builder()
                    : props.builder()).correlationId(replyId)
                    .replyTo(this.replyQueue).build();
            this.continuationMap.put(replyId, k);
        }
        publish(exchange, routingKey, props, message);
        Object reply = k.uninterruptibleGet(this.timeout);
        if (reply instanceof ShutdownSignalException) {
            ShutdownSignalException sig = (ShutdownSignalException) reply;
            ShutdownSignalException wrapper = new ShutdownSignalException(
                    sig.isHardError(), sig.isInitiatedByApplication(),
                    sig.getReason(), sig.getReference());
            wrapper.initCause(sig);
            throw wrapper;
        } else {
            return (byte[]) reply;
        }
    }

    /**
     * Perform a simple RPC, blocking until a response is received.
     *
     * @param exchange to which RPC is sent
     * @param routingKey for request on exchange
     * @param message the byte array request to send
     * @return the byte array response received
     * @throws ShutdownSignalException if the connection or channel dies before
     *             a response is received.
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a response is not received within the
     *             configured timeout
     */
    public byte[] call(String exchange, String routingKey, byte[] message) throws IOException,
            ShutdownSignalException, TimeoutException {
        return primitiveCall(exchange, routingKey, null, message);
    }

    /**
     * Retrieve the reply queue.
     *
     * @return the name of the client's reply queue
     */
    public String getReplyQueue() {
        return this.replyQueue;
    }

}
