// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;
import com.rabbitmq.utility.BlockingCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class which manages simple RPC-style communication.
 * The class is agnostic about the format of RPC arguments / return values.
 * It simply provides a mechanism for sending a message to an exchange with a given routing key,
 * and waiting for a response.
*/
public class RpcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);

    /** Channel we are communicating on */
    private final Channel _channel;
    /** Exchange to send requests to */
    private final String _exchange;
    /** Routing key to use for requests */
    private final String _routingKey;
    /** Queue where the server should put the reply */
    private final String _replyTo;
    /** timeout to use on call responses */
    private final int _timeout;
    /** NO_TIMEOUT value must match convention on {@link BlockingCell#uninterruptibleGet(int)} */
    protected final static int NO_TIMEOUT = -1;
    /** Whether to publish RPC requests with the mandatory flag or not. */
    private final boolean _useMandatory;

    public final static Function<Object, Response> DEFAULT_REPLY_HANDLER = reply -> {
        if (reply instanceof ShutdownSignalException) {
            ShutdownSignalException sig = (ShutdownSignalException) reply;
            ShutdownSignalException wrapper =
                    new ShutdownSignalException(sig.isHardError(),
                            sig.isInitiatedByApplication(),
                            sig.getReason(),
                            sig.getReference());
            wrapper.initCause(sig);
            throw wrapper;
        } else if (reply instanceof UnroutableRpcRequestException) {
            throw (UnroutableRpcRequestException) reply;
        } else {
            return (Response) reply;
        }
    };

    private final Function<Object, Response> _replyHandler;

    /** Map from request correlation ID to continuation BlockingCell */
    private final Map<String, BlockingCell<Object>> _continuationMap = new HashMap<String, BlockingCell<Object>>();
    /** Contains the most recently-used request correlation ID */
    private int _correlationId;

    /** Consumer attached to our reply queue */
    private DefaultConsumer _consumer;

    /**
     * Construct a {@link RpcClient} with the passed-in {@link RpcClientParams}.
     *
     * @param params
     * @throws IOException
     * @see RpcClientParams
     * @since 5.6.0
     */
    public RpcClient(RpcClientParams params) throws
            IOException {
        _channel = params.getChannel();
        _exchange = params.getExchange();
        _routingKey = params.getRoutingKey();
        _replyTo = params.getReplyTo();
        if (params.getTimeout() < NO_TIMEOUT) {
            throw new IllegalArgumentException("Timeout argument must be NO_TIMEOUT(-1) or non-negative.");
        }
        _timeout = params.getTimeout();
        _useMandatory = params.shouldUseMandatory();
        _replyHandler = params.getReplyHandler();
        _correlationId = 0;

        _consumer = setupConsumer();
        if (_useMandatory) {
            this._channel.addReturnListener(returnMessage -> {
                synchronized (_continuationMap) {
                    String replyId = returnMessage.getProperties().getCorrelationId();
                    BlockingCell<Object> blocker = _continuationMap.remove(replyId);
                    if (blocker == null) {
                        // Entry should have been removed if request timed out,
                        // log a warning nevertheless.
                        LOGGER.warn("No outstanding request for correlation ID {}", replyId);
                    } else {
                        blocker.set(new UnroutableRpcRequestException(returnMessage));
                    }
                }
            });
        }
    }

    /**
     * Private API - ensures the RpcClient is correctly open.
     * @throws IOException if an error is encountered
     */
    public void checkConsumer() throws IOException {
        if (_consumer == null) {
            throw new EOFException("RpcClient is closed");
        }
    }

    /**
     * Public API - cancels the consumer, thus deleting the temporary queue, and marks the RpcClient as closed.
     * @throws IOException if an error is encountered
     */
    public void close() throws IOException {
        if (_consumer != null) {
            _channel.basicCancel(_consumer.getConsumerTag());
            _consumer = null;
        }
    }

    /**
     * Registers a consumer on the reply queue.
     * @throws IOException if an error is encountered
     * @return the newly created and registered consumer
     */
    protected DefaultConsumer setupConsumer() throws IOException {
        DefaultConsumer consumer = new DefaultConsumer(_channel) {
            @Override
            public void handleShutdownSignal(String consumerTag,
                                             ShutdownSignalException signal) {
                synchronized (_continuationMap) {
                    for (Entry<String, BlockingCell<Object>> entry : _continuationMap.entrySet()) {
                        entry.getValue().set(signal);
                    }
                    _consumer = null;
                }
            }

            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body)
                    throws IOException {
                synchronized (_continuationMap) {
                    String replyId = properties.getCorrelationId();
                    BlockingCell<Object> blocker =_continuationMap.remove(replyId);
                    if (blocker == null) {
                        // Entry should have been removed if request timed out,
                        // log a warning nevertheless.
                        LOGGER.warn("No outstanding request for correlation ID {}", replyId);
                    } else {
                        blocker.set(new Response(consumerTag, envelope, properties, body));
                    }
                }
            }
        };
        _channel.basicConsume(_replyTo, true, consumer);
        return consumer;
    }

    public void publish(AMQP.BasicProperties props, byte[] message)
        throws IOException
    {
        _channel.basicPublish(_exchange, _routingKey, _useMandatory, props, message);
    }

    public Response doCall(AMQP.BasicProperties props, byte[] message)
        throws IOException, TimeoutException {
        return doCall(props, message, _timeout);
    }

    public Response doCall(AMQP.BasicProperties props, byte[] message, int timeout)
        throws IOException, ShutdownSignalException, TimeoutException {
        checkConsumer();
        BlockingCell<Object> k = new BlockingCell<Object>();
        String replyId;
        synchronized (_continuationMap) {
            _correlationId++;
            replyId = "" + _correlationId;
            props = ((props==null) ? new AMQP.BasicProperties.Builder() : props.builder())
                .correlationId(replyId).replyTo(_replyTo).build();
            _continuationMap.put(replyId, k);
        }
        publish(props, message);
        Object reply;
        try {
            reply = k.uninterruptibleGet(timeout);
        } catch (TimeoutException ex) {
            // Avoid potential leak.  This entry is no longer needed by caller.
            _continuationMap.remove(replyId);
            throw ex;
        }
        return _replyHandler.apply(reply);
    }

    public byte[] primitiveCall(AMQP.BasicProperties props, byte[] message)
        throws IOException, ShutdownSignalException, TimeoutException
    {
        return primitiveCall(props, message, _timeout);
    }

    public byte[] primitiveCall(AMQP.BasicProperties props, byte[] message, int timeout)
        throws IOException, ShutdownSignalException, TimeoutException
    {
        return doCall(props, message, timeout).getBody();
    }

    /**
     * Perform a simple byte-array-based RPC roundtrip.
     * @param message the byte array request message to send
     * @return the byte array response received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a response is not received within the configured timeout
     */
    public byte[] primitiveCall(byte[] message)
        throws IOException, ShutdownSignalException, TimeoutException {
        return primitiveCall(null, message);
    }

    /**
     * Perform a simple byte-array-based RPC roundtrip
     *
     * Useful if you need to get at more than just the body of the message
     *
     * @param message the byte array request message to send
     * @return The response object is an envelope that contains all of the data provided to the `handleDelivery` consumer
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a response is not received within the configured timeout
     */
    public Response responseCall(byte[] message) throws IOException, ShutdownSignalException, TimeoutException {
        return responseCall(message, _timeout);
    }

    /**
     * Perform a simple byte-array-based RPC roundtrip
     *
     * Useful if you need to get at more than just the body of the message
     *
     * @param message the byte array request message to send
     * @param timeout milliseconds before timing out on wait for response
     * @return The response object is an envelope that contains all of the data provided to the `handleDelivery` consumer
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a response is not received within the configured timeout
     */
    public Response responseCall(byte[] message, int timeout) throws IOException, ShutdownSignalException, TimeoutException {
        return doCall(null, message, timeout);
    }

    /**
     * Perform a simple string-based RPC roundtrip.
     * @param message the string request message to send
     * @return the string response received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a timeout occurs before the response is received
     */
    @SuppressWarnings("unused")
    public String stringCall(String message)
        throws IOException, ShutdownSignalException, TimeoutException
    {
        byte[] request;
        try {
            request = message.getBytes(StringRpcServer.STRING_ENCODING);
        } catch (IOException _e) {
            request = message.getBytes();
        }
        byte[] reply = primitiveCall(request);
        try {
            return new String(reply, StringRpcServer.STRING_ENCODING);
        } catch (IOException _e) {
           return new String(reply);
        }
    }

    /**
     * Perform an AMQP wire-protocol-table based RPC roundtrip <br><br>
     *
     * There are some restrictions on the values appearing in the table: <br>
     * they must be of type {@link String}, {@link LongString}, {@link Integer}, {@link java.math.BigDecimal}, {@link Date},
     * or (recursively) a {@link Map} of the enclosing type.
     *
     * @param message the table to send
     * @return the table received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a timeout occurs before a response is received
     */
    public Map<String, Object> mapCall(Map<String, Object> message)
        throws IOException, ShutdownSignalException, TimeoutException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(new DataOutputStream(buffer)));
        writer.writeTable(message);
        writer.flush();
        byte[] reply = primitiveCall(buffer.toByteArray());
        MethodArgumentReader reader =
            new MethodArgumentReader(new ValueReader(new DataInputStream(new ByteArrayInputStream(reply))));
        return reader.readTable();
    }

    /**
     * Perform an AMQP wire-protocol-table based RPC roundtrip, first
     * constructing the table from an array of alternating keys (in
     * even-numbered elements, starting at zero) and values (in
     * odd-numbered elements, starting at one) <br>
     * Restrictions on value arguments apply as in {@link RpcClient#mapCall(Map)}.
     *
     * @param keyValuePairs alternating {key, value, key, value, ...} data to send
     * @return the table received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     * @throws TimeoutException if a timeout occurs before a response is received
     */
    public Map<String, Object> mapCall(Object[] keyValuePairs)
        throws IOException, ShutdownSignalException, TimeoutException
    {
        Map<String, Object> message = new HashMap<String, Object>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            message.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return mapCall(message);
    }

    /**
     * Retrieve the channel.
     * @return the channel to which this client is connected
     */
    public Channel getChannel() {
        return _channel;
    }

    /**
     * Retrieve the exchange.
     * @return the exchange to which this client is connected
     */
    public String getExchange() {
        return _exchange;
    }

    /**
     * Retrieve the routing key.
     * @return the routing key for messages to this client
     */
    public String getRoutingKey() {
        return _routingKey;
    }

    /**
     * Retrieve the continuation map.
     * @return the map of objects to blocking cells for this client
     */
    public Map<String, BlockingCell<Object>> getContinuationMap() {
        return _continuationMap;
    }

    /**
     * Retrieve the correlation id.
     * @return the most recently used correlation id
     */
    public int getCorrelationId() {
        return _correlationId;
    }

    /**
     * Retrieve the consumer.
     * @return an interface to the client's consumer object
     */
    public Consumer getConsumer() {
        return _consumer;
    }

    /**
     * The response object is an envelope that contains all of the data provided to the `handleDelivery` consumer
     */
    public static class Response {
        protected String consumerTag;
        protected Envelope envelope;
        protected AMQP.BasicProperties properties;
        protected byte[] body;

        public Response() {
        }

        public Response(
            final String consumerTag, final Envelope envelope, final AMQP.BasicProperties properties,
            final byte[] body) {
            this.consumerTag = consumerTag;
            this.envelope = envelope;
            this.properties = properties;
            this.body = body;
        }

        public String getConsumerTag() {
            return consumerTag;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public AMQP.BasicProperties getProperties() {
            return properties;
        }

        public byte[] getBody() {
            return body;
        }
    }
}

