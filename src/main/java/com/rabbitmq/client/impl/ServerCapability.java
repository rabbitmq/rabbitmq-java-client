// Copyright (c) 2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl;

/**
 * Capabilities a RabbitMQ node advertises in the <code>capabilities</code> table
 * of the <code>connection.start</code> server properties.
 */
enum ServerCapability {

    PUBLISHER_CONFIRMS("publisher_confirms"),
    EXCHANGE_EXCHANGE_BINDINGS("exchange_exchange_bindings"),
    BASIC_NACK("basic.nack"),
    CONSUMER_CANCEL_NOTIFY("consumer_cancel_notify"),
    CONNECTION_BLOCKED("connection.blocked"),
    CONSUMER_PRIORITIES("consumer_priorities"),
    AUTHENTICATION_FAILURE_CLOSE("authentication_failure_close"),
    PER_CONSUMER_QOS("per_consumer_qos"),
    DIRECT_REPLY_TO("direct_reply_to"),
    ACCEPT_CONSUMER_CANCEL_OK("accept_consumer_cancel_ok");

    private final String name;

    ServerCapability(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
