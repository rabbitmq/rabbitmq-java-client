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

import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * Encapsulates the response from a {@link Channel#basicGet} message-retrieval method call
 * - essentially a static bean "holder" with message response data.
 */
public class GetResponse {
    private final Envelope envelope;
    private final BasicProperties props;
    private final byte[] body;
    private final int messageCount;

    /**
     * Construct a {@link GetResponse} with the specified construction parameters
     * @param envelope the {@link Envelope}
     * @param props message properties
     * @param body the message body
     * @param messageCount the number of messages in the response
     */
    public GetResponse(Envelope envelope, BasicProperties props, byte[] body, int messageCount)
    {
        this.envelope = envelope;
        this.props = props;
        this.body = body;
        this.messageCount = messageCount;
    }

    /**
     * Get the {@link Envelope} included in this response
     * @return the envelope
     */
    public Envelope getEnvelope() {
        return envelope;
    }

    /**
     * Get the {@link BasicProperties} included in this response
     * @return the properties
     */
    public BasicProperties getProps() {
        return props;
    }

    /**
     * Get the message body included in this response
     * @return the message body
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Get the message count included in this response
     * @return the message count
     */
    public int getMessageCount() {
        return messageCount;
    }
}
