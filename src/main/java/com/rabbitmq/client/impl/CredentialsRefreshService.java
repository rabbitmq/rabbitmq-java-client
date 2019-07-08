// Copyright (c) 2019 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.impl;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Provider interface to refresh credentials when appropriate
 * and perform an operation once the credentials have been
 * renewed. In the context of RabbitMQ, the operation consists
 * in calling the <code>update.secret</code> AMQP extension
 * to provide new valid credentials before the current ones
 * expire.
 * <p>
 * New connections are registered and implementations must perform
 * credentials renewal when appropriate. Implementations
 * must call a registered callback once credentials are renewed.
 *
 * @see CredentialsProvider
 * @see DefaultCredentialsRefreshService
 */
public interface CredentialsRefreshService {

    /**
     * Register a new entity that needs credentials renewal.
     * <p>
     * The registered callback must return true if the action was
     * performed correctly, throw an exception if something goes wrong,
     * and return false if it became stale and wants to be unregistered.
     * <p>
     * Implementations are free to automatically unregister an entity whose
     * callback has failed a given number of times.
     *
     * @param credentialsProvider the credentials provider
     * @param refreshAction       the action to perform after credentials renewal
     * @return a tracking ID for the registration
     */
    String register(CredentialsProvider credentialsProvider, Callable<Boolean> refreshAction);

    /**
     * Unregister the entity with the given registration ID.
     * <p>
     * Its state is cleaned up and its registered callback will not be
     * called again.
     *
     * @param credentialsProvider the credentials provider
     * @param registrationId      the registration ID
     */
    void unregister(CredentialsProvider credentialsProvider, String registrationId);

    /**
     * Provide a hint about whether credentials should be renewed now or not before attempting to connect.
     * <p>
     * This can avoid a connection to use almost expired credentials if this connection
     * is created just before credentials are refreshed in the background, but does not
     * benefit from the refresh.
     *
     * @param timeBeforeExpiration
     * @return true if credentials should be renewed, false otherwise
     */
    boolean isApproachingExpiration(Duration timeBeforeExpiration);

}
