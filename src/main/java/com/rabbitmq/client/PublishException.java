// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import java.io.IOException;

/**
 * Exception a {@link ConfirmationPublisher} future completes with when the
 * broker rejects a message ({@code basic.nack}) or returns it as unroutable
 * ({@code basic.return}).
 * <p>
 * Use {@link #isReturn()} to distinguish the two cases. For a return,
 * {@link #getReturned()} carries the full {@link Return} (reply code and text,
 * exchange, routing key, properties, body); for a nack it is null.
 *
 * @see ConfirmationPublisher#basicPublishAsync(String, String, boolean, com.rabbitmq.client.AMQP.BasicProperties, byte[], Object)
 */
public class PublishException extends IOException {

    private final long sequenceNumber;
    private final Return returned;
    private final Object context;

    /**
     * Constructor for the {@code basic.nack} case.
     *
     * @param sequenceNumber the publish sequence number
     * @param context the user-provided context object
     */
    public PublishException(long sequenceNumber, Object context) {
        super(String.format("Message %d nack'd", sequenceNumber));
        this.sequenceNumber = sequenceNumber;
        this.returned = null;
        this.context = context;
    }

    /**
     * Constructor for the {@code basic.return} case.
     *
     * @param sequenceNumber the publish sequence number
     * @param returned the broker's return
     * @param context the user-provided context object
     */
    public PublishException(long sequenceNumber, Return returned, Object context) {
        super(String.format("Message %d returned: %s (%d)", sequenceNumber,
            returned.getReplyText(), returned.getReplyCode()));
        this.sequenceNumber = sequenceNumber;
        this.returned = returned;
        this.context = context;
    }

    /**
     * @return the publish sequence number of the failed message
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * @return true if caused by {@code basic.return} (unroutable message),
     *         false if caused by {@code basic.nack} (broker rejection)
     */
    public boolean isReturn() {
        return returned != null;
    }

    /**
     * @return the broker's {@link Return} for the unroutable message, or null
     *         for a nack
     */
    public Return getReturned() {
        return returned;
    }

    /**
     * @return the user-provided context object passed to {@code basicPublishAsync},
     *         or null if none was provided
     */
    public Object getContext() {
        return context;
    }
}
