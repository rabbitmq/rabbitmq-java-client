//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

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

import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;
import com.rabbitmq.utility.BlockingCell;

/**
 * Convenience class which manages a temporary reply queue for simple RPC-style communication.
 * The class is agnostic about the format of RPC arguments / return values.
 * It simply provides a mechanism for sending a message to an exchange with a given routing key,
 * and waiting for a response on a reply queue.
*/
public class RpcClient {
    /** Channel we are communicating on */
    private final Channel _channel;
    /** Exchange to send requests to */
    private final String _exchange;
    /** Routing key to use for requests */
    private final String _routingKey;

    /** Map from request correlation ID to continuation BlockingCell */
    private final Map<String, BlockingCell<Object>> _continuationMap = new HashMap<String, BlockingCell<Object>>();
    /** Contains the most recently-used request correlation ID */
    private int _correlationId;

    /** The name of our private reply queue */
    private String _replyQueue;
    /** Consumer attached to our reply queue */
    private DefaultConsumer _consumer;

    /**
     * Construct a new RpcClient that will communicate on the given channel, sending
     * requests to the given exchange with the given routing key.
     * <p>
     * Causes the creation of a temporary private autodelete queue.
     * @param channel the channel to use for communication
     * @param exchange the exchange to connect to
     * @param routingKey the routing key
     * @throws IOException if an error is encountered
     * @see #setupReplyQueue
     */
    public RpcClient(Channel channel, String exchange, String routingKey) throws IOException {
        _channel = channel;
        _exchange = exchange;
        _routingKey = routingKey;
        _correlationId = 0;

        _replyQueue = setupReplyQueue();
        _consumer = setupConsumer();
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
     * Creates a server-named exclusive autodelete queue to use for
     * receiving replies to RPC requests.
     * @throws IOException if an error is encountered
     * @return the name of the reply queue
     */
    private String setupReplyQueue() throws IOException {
        return _channel.queueDeclare("", false, false, true, true, null).getQueue();
    }

    /**
     * Registers a consumer on the reply queue.
     * @throws IOException if an error is encountered
     * @return the newly created and registered consumer
     */
    private DefaultConsumer setupConsumer() throws IOException {
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
                    String replyId = properties.correlationId;
                    BlockingCell<Object> blocker = _continuationMap.get(replyId);
                    _continuationMap.remove(replyId);
                    blocker.set(body);
                }
            }
        };
        _channel.basicConsume(_replyQueue, true, consumer);
        return consumer;
    }

    public void publish(AMQP.BasicProperties props, byte[] message)
        throws IOException
    {
        _channel.basicPublish(_exchange, _routingKey, props, message);
    }

    public byte[] primitiveCall(AMQP.BasicProperties props, byte[] message)
        throws IOException, ShutdownSignalException
    {
        checkConsumer();
        BlockingCell<Object> k = new BlockingCell<Object>();
        synchronized (_continuationMap) {
            _correlationId++;
            String replyId = "" + _correlationId;
            if (props != null) {
                props.correlationId = replyId;
                props.replyTo = _replyQueue;
            }
            else {
                props = new AMQP.BasicProperties(null, null, null, null,
                                             null, replyId,
                                             _replyQueue, null, null, null,
                                             null, null, null, null);
            }
            _continuationMap.put(replyId, k);
        }
        publish(props, message);
        Object reply = k.uninterruptibleGet();
        if (reply instanceof ShutdownSignalException) {
            ShutdownSignalException sig = (ShutdownSignalException) reply;
            ShutdownSignalException wrapper =
                new ShutdownSignalException(sig.isHardError(),
                                            sig.isInitiatedByApplication(),
                                            sig.getReason(),
                                            sig.getReference());
            wrapper.initCause(sig);
            throw wrapper;
        } else {
            return (byte[]) reply;
        }
    }

    /**
     * Perform a simple byte-array-based RPC roundtrip.
     * @param message the byte array request message to send
     * @return the byte array response received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     */
    public byte[] primitiveCall(byte[] message)
        throws IOException, ShutdownSignalException {
        return primitiveCall(null, message);
    }

    /**
     * Perform a simple string-based RPC roundtrip.
     * @param message the string request message to send
     * @return the string response received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     */
    public String stringCall(String message)
        throws IOException, ShutdownSignalException
    {
        return new String(primitiveCall(message.getBytes()));
    }

    /**
     * Perform an AMQP wire-protocol-table based RPC roundtrip <br><br>
     *
     * There are some restrictions on the values appearing in the table: <br>
     * they must be of type {@link String}, {@link com.rabbitmq.client.impl.LongString}, {@link Integer}, {@link java.math.BigDecimal}, {@link Date},
     * or (recursively) a {@link Map} of the enclosing type.
     *
     * @param message the table to send
     * @return the table received
     * @throws ShutdownSignalException if the connection dies during our wait
     * @throws IOException if an error is encountered
     */
    public Map<String, Object> mapCall(Map<String, Object> message)
        throws IOException, ShutdownSignalException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new DataOutputStream(buffer));
        writer.writeTable(message);
        writer.flush();
        byte[] reply = primitiveCall(buffer.toByteArray());
        MethodArgumentReader reader =
            new MethodArgumentReader(new DataInputStream(new ByteArrayInputStream(reply)));
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
     */
    public Map<String, Object> mapCall(Object[] keyValuePairs)
        throws IOException, ShutdownSignalException
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
     * Retrieve the reply queue.
     * @return the name of the client's reply queue
     */
    public String getReplyQueue() {
        return _replyQueue;
    }

    /**
     * Retrieve the consumer.
     * @return an interface to the client's consumer object
     */
    public Consumer getConsumer() {
        return _consumer;
    }
}

