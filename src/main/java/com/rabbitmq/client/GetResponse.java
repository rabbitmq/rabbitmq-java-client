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
     * @param messageCount the server's most recent estimate of the number of messages remaining on the queue
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
     * Get the server's most recent estimate of the number of messages
     * remaining on the queue. This number can only ever be a rough
     * estimate, because of concurrent activity at the server and the
     * delay between the server sending its estimate and the client
     * receiving and processing the message containing the estimate.
     *
     * <p>According to the AMQP specification, this figure does not
     * include the message being delivered. For example, this field
     * will be zero in the simplest case of a single reader issuing a
     * Basic.Get on a private queue holding a single message (the
     * message being delivered in this GetResponse).
     *
     * @return an estimate of the number of messages remaining to be
     * read from the queue
     */
    public int getMessageCount() {
        return messageCount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GetResponse(envelope=").append(envelope);
        sb.append(", props=").append(props);
        sb.append(", messageCount=").append(messageCount);
        sb.append(", body=(elided, ").append(body.length).append(" bytes long)");
        sb.append(")");
        return sb.toString();
    }
}
