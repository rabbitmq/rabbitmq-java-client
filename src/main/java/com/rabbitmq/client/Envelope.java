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

/**
 * Encapsulates a group of parameters used for AMQP's Basic methods
 */
public class Envelope {
    private final long _deliveryTag;
    private final boolean _redeliver;
    private final String _exchange;
    private final String _routingKey;

    /**
     * Construct an {@link Envelope} with the specified construction parameters
     * @param deliveryTag the delivery tag
     * @param redeliver true if this is a redelivery following a failed ack
     * @param exchange the exchange used for the current operation
     * @param routingKey the associated routing key
     */
    public Envelope(long deliveryTag, boolean redeliver, String exchange, String routingKey) {
        this._deliveryTag = deliveryTag;
        this._redeliver = redeliver;
        this._exchange = exchange;
        this._routingKey = routingKey;
    }

    /**
     * Get the delivery tag included in this parameter envelope
     * @return the delivery tag
     */
    public long getDeliveryTag() {
        return _deliveryTag;
    }

    /**
     * Get the redelivery flag included in this parameter envelope. This is a
     * hint as to whether this message may have been delivered before (but not
     * acknowledged). If the flag is not set, the message definitely has not
     * been delivered before. If it is set, it may have been delivered before.
     *
     * @return the redelivery flag
     */
    public boolean isRedeliver() {
        return _redeliver;
    }

    /**
     * Get the name of the exchange included in this parameter envelope
     * @return the exchange
     */
    public String getExchange() {
        return _exchange;
    }

    /**
     * Get the routing key included in this parameter envelope
     * @return the routing key
     */
    public String getRoutingKey() {
        return _routingKey;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Envelope(deliveryTag=").append(_deliveryTag);
        sb.append(", redeliver=").append(_redeliver);
        sb.append(", exchange=").append(_exchange);
        sb.append(", routingKey=").append(_routingKey);
        sb.append(")");
        return sb.toString();
    }
}
