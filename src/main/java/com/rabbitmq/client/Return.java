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
 *
 */
public class Return {

    private final int replyCode;
    private final String replyText;
    private final String exchange;
    private final String routingKey;
    private final AMQP.BasicProperties properties;
    private final byte[] body;

    public Return(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body) {
        this.replyCode = replyCode;
        this.replyText = replyText;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.properties = properties;
        this.body = body;
    }

    public int getReplyCode() {
        return replyCode;
    }

    public String getReplyText() {
        return replyText;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public AMQP.BasicProperties getProperties() {
        return properties;
    }

    public byte[] getBody() {
        return body;
    }
}
