// Copyright (c) 2017 Pivotal Software, Inc.  All rights reserved.
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
 * Encapsulates an arbitrary message - simple "bean" holder structure.
 */
public class Delivery {
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
    public AMQP.BasicProperties getProperties() {
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
