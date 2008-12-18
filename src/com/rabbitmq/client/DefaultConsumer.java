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

/**
 * Convenience class providing a default implementation of {@link Consumer}.
 * We anticipate that most Consumer implementations will subclass this class.
 */
public class DefaultConsumer implements Consumer {
    /** Channel that this consumer is associated with. */
    private final Channel _channel;
    /** Consumer tag for this consumer. */
    private String _consumerTag;

    /**
     * Constructs a new instance and records its association to the passed-in channel.
     * @param channel the channel on which the
     */
    public DefaultConsumer(Channel channel) {
        _channel = channel;
    }

    /**
     * Stores the most recently passed-in consumerTag - semantically, there should be only one.
     * @see Consumer#handleConsumeOk
     */
    public void handleConsumeOk(String consumerTag) {
        this._consumerTag = consumerTag;
    }

    /**
     * No-op implementation of {@link Consumer#handleCancelOk}.
     * @param consumerTag the defined consumer tag (client- or server-generated)
     */
    public void handleCancelOk(String consumerTag) {
        // no work to do
    }

    /**
     * No-op implementation of {@link Consumer#handleShutdownSignal}.
     */
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        // no work to do
    }

    /**
     * No-op implementation of {@link Consumer#handleDelivery}.
     */
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body)
        throws IOException
    {
            // no work to do
    }

    /**
    *  Retrieve the channel.
     * @return the channel this consumer is attached to.
     */
    public Channel getChannel() {
        return _channel;
    }

    /**
    *  Retrieve the consumer tag.
     * @return the most recently notified consumer tag.
     */
    public String getConsumerTag() {
        return _consumerTag;
    }
}

