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

import java.util.EventListener;

/**
 * A ShutdownListener receives information about the shutdown of connections and
 * channels. Note that when a connection is shut down, its associated channels are also
 * considered shut down and their ShutdownListeners will be notified (with the same cause).
 * Because of this, and the fact that channel ShutdownListeners execute in the connection's
 * thread, attempting to make blocking calls on a connection inside the listener will
 * lead to deadlock.
 *
 * @see ShutdownNotifier
 * @see ShutdownSignalException
 */
@FunctionalInterface
public interface ShutdownListener extends EventListener {
    void shutdownCompleted(ShutdownSignalException cause);
}
