// Copyright (c) 2018-2019 Pivotal Software, Inc.  All rights reserved.
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

/**
 * Provider interface for establishing credentials for connecting to the broker. Especially useful
 * for situations where credentials might expire or change before a recovery takes place or where it is
 * convenient to plug in an outside custom implementation.
 *
 * @see CredentialsRefreshService
 * @since 5.2.0
 */
public interface CredentialsProvider {

    /**
     * Username to use for authentication
     *
     * @return username
     */
    String getUsername();

    /**
     * Password/secret/token to use for authentication
     *
     * @return password/secret/token
     */
    String getPassword();

    /**
     * The time before the credentials expire, if they do expire.
     * <p>
     * If credentials do not expire, must return null. Default
     * behavior is to return null, assuming credentials never
     * expire.
     *
     * @return time before expiration
     */
    default Duration getTimeBeforeExpiration() {
        return null;
    }

    /**
     * Instructs the provider to refresh or renew credentials.
     * <p>
     * Default behavior is no-op.
     */
    default void refresh() {
        // no need to refresh anything by default
    }

}