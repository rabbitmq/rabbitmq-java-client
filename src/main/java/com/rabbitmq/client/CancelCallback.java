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

import java.io.IOException;
import java.util.Map;

/**
 * Callback interface to be notified of the cancellation of a consumer.
 * Prefer it over {@link Consumer} for a lambda-oriented syntax,
 * if you don't need to implement all the application callbacks.
 * @see DeliverCallback
 * @see ConsumerShutdownSignalCallback
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, CancelCallback)
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, ConsumerShutdownSignalCallback)
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, Map, DeliverCallback, CancelCallback, ConsumerShutdownSignalCallback)
 * @since 5.0
 */
@FunctionalInterface
public interface CancelCallback {

    /**
     * Called when the consumer is cancelled for reasons <i>other than</i> by a call to
     * {@link Channel#basicCancel}. For example, the queue has been deleted.
     * See {@link Consumer#handleCancelOk} for notification of consumer
     * cancellation due to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @throws IOException
     */
    void handle(String consumerTag) throws IOException;

}
