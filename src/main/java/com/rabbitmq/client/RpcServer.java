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

import com.rabbitmq.utility.Utility;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class which manages a request queue for a simple RPC-style service.
 * The class is agnostic about the format of RPC arguments / return values.
*/
public class RpcServer {
    /** Channel we are communicating on */
    private final Channel _channel;
    /** Queue to receive requests from */
    private final String _queueName;
    /** Boolean controlling the exit from the mainloop. */
    private volatile boolean _mainloopRunning = true;

    /** Consumer attached to our request queue */
    private RpcConsumer _consumer;

    /**
     * Creates an RpcServer listening on a temporary exclusive
     * autodelete queue.
     */
    public RpcServer(Channel channel)
        throws IOException
    {
        this(channel, null);
    }

    /**
     * If the passed-in queue name is null, creates a server-named
     * temporary exclusive autodelete queue to use; otherwise expects
     * the queue to have already been declared.
     */
    public RpcServer(Channel channel, String queueName)
        throws IOException
    {
        _channel = channel;
        if (queueName == null || queueName.equals("")) {
            _queueName = _channel.queueDeclare().getQueue();
        } else {
            _queueName = queueName;
        }
        _consumer = setupConsumer();
    }

    /**
     * Public API - cancels the consumer, thus deleting the queue, if
     * it was a temporary queue, and marks the RpcServer as closed.
     * @throws IOException if an error is encountered
     */
    public void close()
        throws IOException
    {
        if (_consumer != null) {
            _channel.basicCancel(_consumer.getConsumerTag());
            _consumer = null;
        }
        terminateMainloop();
    }

    /**
     * Registers a consumer on the reply queue.
     * @throws IOException if an error is encountered
     * @return the newly created and registered consumer
     */
    protected RpcConsumer setupConsumer()
        throws IOException
    {
        RpcConsumer consumer = new DefaultRpcConsumer(_channel);
        _channel.basicConsume(_queueName, consumer);
        return consumer;
    }

    /**
     * Public API - main server loop. Call this to begin processing
     * requests. Request processing will continue until the Channel
     * (or its underlying Connection) is shut down, or until
     * terminateMainloop() is called, or until the thread running the loop
     * is interrupted.
     *
     * Note that if the mainloop is blocked waiting for a request, the
     * termination flag is not checked until a request is received, so
     * a good time to call terminateMainloop() is during a request
     * handler.
     *
     * @return the exception that signalled the Channel shutdown, or null for orderly shutdown
     */
    public ShutdownSignalException mainloop()
        throws IOException
    {
        try {
            while (_mainloopRunning) {
                Delivery request;
                try {
                    request = _consumer.nextDelivery();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    _mainloopRunning = false;
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
     * Call this method to terminate the mainloop.
     *
     * Note that if the mainloop is blocked waiting for a request, the
     * termination flag is not checked until a request is received, so
     * a good time to call terminateMainloop() is during a request
     * handler.
     */
    public void terminateMainloop() {
        _mainloopRunning = false;
    }

    /**
     * Private API - Process a single request. Called from mainloop().
     */
    public void processRequest(Delivery request)
        throws IOException
    {
        AMQP.BasicProperties requestProperties = request.getProperties();
        String correlationId = requestProperties.getCorrelationId();
        String replyTo = requestProperties.getReplyTo();
        if (correlationId != null && replyTo != null)
        {
            AMQP.BasicProperties.Builder replyPropertiesBuilder
                = new AMQP.BasicProperties.Builder().correlationId(correlationId);
            AMQP.BasicProperties replyProperties = preprocessReplyProperties(request, replyPropertiesBuilder);
            byte[] replyBody = handleCall(request, replyProperties);
            replyProperties = postprocessReplyProperties(request, replyProperties.builder());
            _channel.basicPublish("", replyTo, replyProperties, replyBody);
        } else {
            handleCast(request);
        }
    }

    /**
     * Lowest-level response method. Calls
     * handleCall(AMQP.BasicProperties,byte[],AMQP.BasicProperties).
     */
    public byte[] handleCall(Delivery request,
                             AMQP.BasicProperties replyProperties)
    {
        return handleCall(request.getProperties(),
                          request.getBody(),
                          replyProperties);
    }

    /**
     * Mid-level response method. Calls
     * handleCall(byte[],AMQP.BasicProperties).
     */
    public byte[] handleCall(AMQP.BasicProperties requestProperties,
                             byte[] requestBody,
                             AMQP.BasicProperties replyProperties)
    {
        return handleCall(requestBody, replyProperties);
    }

    /**
     * High-level response method. Returns an empty response by
     * default - override this (or other handleCall and handleCast
     * methods) in subclasses.
     */
    public byte[] handleCall(byte[] requestBody,
                             AMQP.BasicProperties replyProperties)
    {
        return new byte[0];
    }

    /**
     * Gives a chance to set/modify reply properties before handling call.
     * Note the correlationId property is already set.
     * @param request the inbound message
     * @param builder the reply properties builder
     * @return the properties to pass in to the handling call
     */
    protected AMQP.BasicProperties preprocessReplyProperties(Delivery request, AMQP.BasicProperties.Builder builder) {
        return builder.build();
    }

    /**
     * Gives a chance to set/modify reply properties after the handling call
     * @param request the inbound message
     * @param builder the reply properties builder
     * @return the properties to pass in to the response message
     */
    protected AMQP.BasicProperties postprocessReplyProperties(Delivery request, AMQP.BasicProperties.Builder builder) {
        return builder.build();
    }

    /**
     * Lowest-level handler method. Calls
     * handleCast(AMQP.BasicProperties,byte[]).
     */
    public void handleCast(Delivery request)
    {
        handleCast(request.getProperties(), request.getBody());
    }

    /**
     * Mid-level handler method. Calls
     * handleCast(byte[]).
     */
    public void handleCast(AMQP.BasicProperties requestProperties, byte[] requestBody)
    {
        handleCast(requestBody);
    }

    /**
     * High-level handler method. Does nothing by default - override
     * this (or other handleCast and handleCast methods) in
     * subclasses.
     */
    public void handleCast(byte[] requestBody)
    {
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

    public interface RpcConsumer extends Consumer {

        Delivery nextDelivery() throws InterruptedException, ShutdownSignalException, ConsumerCancelledException;

        String getConsumerTag();

    }

    private static class DefaultRpcConsumer extends DefaultConsumer implements RpcConsumer {

        // Marker object used to signal the queue is in shutdown mode.
        // It is only there to wake up consumers. The canonical representation
        // of shutting down is the presence of _shutdown.
        // Invariant: This is never on _queue unless _shutdown != null.
        private static final Delivery POISON = new Delivery(null, null, null);
        private final BlockingQueue<Delivery> _queue;
        // When this is non-null the queue is in shutdown mode and nextDelivery should
        // throw a shutdown signal exception.
        private volatile ShutdownSignalException _shutdown;
        private volatile ConsumerCancelledException _cancelled;

        public DefaultRpcConsumer(Channel ch) {
            this(ch, new LinkedBlockingQueue<>());
        }

        public DefaultRpcConsumer(Channel ch, BlockingQueue<Delivery> q) {
            super(ch);
            this._queue = q;
        }

        @Override
        public Delivery nextDelivery() throws InterruptedException, ShutdownSignalException, ConsumerCancelledException {
            return handle(_queue.take());
        }

        @Override
        public void handleShutdownSignal(String consumerTag,
            ShutdownSignalException sig) {
            _shutdown = sig;
            _queue.add(POISON);
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            _cancelled = new ConsumerCancelledException();
            _queue.add(POISON);
        }

        @Override
        public void handleDelivery(String consumerTag,
            Envelope envelope,
            AMQP.BasicProperties properties,
            byte[] body)
            throws IOException {
            checkShutdown();
            this._queue.add(new Delivery(envelope, properties, body));
        }

        /**
         * Check if we are in shutdown mode and if so throw an exception.
         */
        private void checkShutdown() {
            if (_shutdown != null)
                throw Utility.fixStackTrace(_shutdown);
        }

        /**
         * If delivery is not POISON nor null, return it.
         * <p/>
         * If delivery, _shutdown and _cancelled are all null, return null.
         * <p/>
         * If delivery is POISON re-insert POISON into the queue and
         * throw an exception if POISONed for no reason.
         * <p/>
         * Otherwise, if we are in shutdown mode or cancelled,
         * throw a corresponding exception.
         */
        private Delivery handle(Delivery delivery) {
            if (delivery == POISON ||
                delivery == null && (_shutdown != null || _cancelled != null)) {
                if (delivery == POISON) {
                    _queue.add(POISON);
                    if (_shutdown == null && _cancelled == null) {
                        throw new IllegalStateException(
                            "POISON in queue, but null _shutdown and null _cancelled. " +
                                "This should never happen, please report as a BUG");
                    }
                }
                if (null != _shutdown)
                    throw Utility.fixStackTrace(_shutdown);
                if (null != _cancelled)
                    throw Utility.fixStackTrace(_cancelled);
            }
            return delivery;
        }
    }


}

