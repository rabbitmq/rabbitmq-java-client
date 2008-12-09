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

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.utility.ValueOrException;

/**
 * Convenience class: an implementation of {@link Consumer} with straightforward blocking semantics
 */
public class QueueingConsumer extends DefaultConsumer {
    public BlockingQueue<ValueOrException<Delivery, ShutdownSignalException>> _queue;

    public QueueingConsumer(Channel ch) {
        this(ch,
             new LinkedBlockingQueue<ValueOrException<Delivery, ShutdownSignalException>>());
    }

    public QueueingConsumer(Channel ch,
                            BlockingQueue<ValueOrException<Delivery, ShutdownSignalException>> q)
    {
        super(ch);
        this._queue = q;
    }

    @Override public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        this._queue.add(ValueOrException. <Delivery, ShutdownSignalException> makeException(sig));
    }

    @Override public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body)
        throws IOException
    {
        this._queue.add(ValueOrException. <Delivery, ShutdownSignalException> makeValue
                        (new Delivery(envelope, properties, body)));
    }

    /**
     * Encapsulates an arbitrary message - simple "bean" holder structure.
     */
    public static class Delivery {
        private final Envelope _envelope;
        private final AMQP.BasicProperties _properties;
        private final byte[] _body;

        public Delivery(Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
            _envelope = envelope;
            _properties = properties;
            _body = body;
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
    }

    /**
     * Main application-side API: wait for the next message delivery and return it.
     * @return the next message
     * @throws InterruptedException if an interrupt is received while waiting
     * @throws ShutdownSignalException if the connection is shut down while waiting
     */
    public Delivery nextDelivery()
        throws InterruptedException, ShutdownSignalException
    {
        return _queue.take().getValue();
    }

    /**
     * Main application-side API: wait for the next message delivery and return it.
     * @param timeout timeout in millisecond
     * @return the next message or null if timed out
     * @throws InterruptedException if an interrupt is received while waiting
     * @throws ShutdownSignalException if the connection is shut down while waiting
     */
    public Delivery nextDelivery(long timeout)
        throws InterruptedException, ShutdownSignalException
    {
        ValueOrException<Delivery, ShutdownSignalException> r =
            _queue.poll(timeout, TimeUnit.MILLISECONDS);
        return r == null ? null : r.getValue();
    }

    /**
     * Retrieve the underlying blocking queue.
     * @return the queue where incoming messages are stored
     */
    public BlockingQueue<ValueOrException<Delivery, ShutdownSignalException>> getQueue() {
        return _queue;
    }
}
