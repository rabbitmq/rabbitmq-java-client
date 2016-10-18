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
 * Provides a way to register (network, AMQP 0-9-1) connection recovery
 * callbacks.
 *
 * When connection recovery is enabled via {@link ConnectionFactory},
 * {@link ConnectionFactory#newConnection()} and {@link Connection#createChannel()}
 * return {@link Recoverable} connections and channels.
 *
 * @see com.rabbitmq.client.impl.recovery.AutorecoveringConnection
 * @see com.rabbitmq.client.impl.recovery.AutorecoveringChannel
 */
public interface Recoverable {
    /**
     * Registers a connection recovery callback.
     *
     * @param listener Callback function
     */
    void addRecoveryListener(RecoveryListener listener);

    void removeRecoveryListener(RecoveryListener listener);
}
