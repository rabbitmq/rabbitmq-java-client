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
 * Interface for components that are shutdown capable and
 * that allow listeners to be added for shutdown signals
 *
 * @see ShutdownListener
 * @see ShutdownSignalException
 */
public interface ShutdownNotifier {
    /**
     * Add shutdown listener.
     * If the component is already closed, handler is fired immediately
     *
     * @param listener {@link ShutdownListener} to the component
     */
    void addShutdownListener(ShutdownListener listener);

    /**
     * Remove shutdown listener for the component.
     *
     * @param listener {@link ShutdownListener} to be removed
     */
    void removeShutdownListener(ShutdownListener listener);

    /**
     * Get the shutdown reason object
     * @return ShutdownSignalException if component is closed, null otherwise
     */
    ShutdownSignalException getCloseReason();

    /**
     * Protected API - notify the listeners attached to the component
     * @see com.rabbitmq.client.ShutdownListener
     */
    void notifyListeners();

    /**
     * Determine whether the component is currently open.
     * Will return false if we are currently closing.
     * Checking this method should be only for information,
     * because of the race conditions - state can change after the call.
     * Instead just execute and try to catch ShutdownSignalException
     * and IOException
     *
     * @return true when component is open, false otherwise
     */
    boolean isOpen();
}
