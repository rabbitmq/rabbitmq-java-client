// Copyright (c) 2017-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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
import java.util.Map;

/**
 * Callback interface to be notified when a message is delivered.
 * Prefer it over {@link Consumer} for a lambda-oriented syntax,
 * if you don't need to implement all the application callbacks.
 * @see CancelCallback
 * @see ConsumerShutdownSignalCallback
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, CancelCallback)
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, ConsumerShutdownSignalCallback)
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, CancelCallback, ConsumerShutdownSignalCallback)
 * @since 5.0
 */
@FunctionalInterface
public interface DeliverCallback {

    /**
     * Called when a <code><b>basic.deliver</b></code> is received for this consumer.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @param message the delivered message
     * @throws IOException if the consumer encounters an I/O error while processing the message
     */
    void handle(String consumerTag, Delivery message) throws IOException;

}
