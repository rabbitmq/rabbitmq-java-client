// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * Ready-to-use instances and builder for {@link SocketConfigurator}.
 * <p>
 * Note {@link SocketConfigurator}s can be combined with
 * {@link SocketConfigurator#andThen(SocketConfigurator)}.
 *
 * @since 5.4.0
 */
public abstract class SocketConfigurators {

    /**
     * Disable Nagle's algorithm.
     */
    public static final SocketConfigurator DISABLE_NAGLE_ALGORITHM = socket -> socket.setTcpNoDelay(true);

    /**
     * Default {@link SocketConfigurator} that disables Nagle's algorithm.
     */
    public static final SocketConfigurator DEFAULT = DISABLE_NAGLE_ALGORITHM;

    /**
     * Enable server hostname validation for TLS connections.
     */
    public static final SocketConfigurator ENABLE_HOSTNAME_VERIFICATION = socket -> {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            SSLParameters sslParameters = enableHostnameVerification(sslSocket.getSSLParameters());
            sslSocket.setSSLParameters(sslParameters);
        }
    };

    static SSLParameters enableHostnameVerification(SSLParameters sslParameters) {
        if (sslParameters == null) {
            sslParameters = new SSLParameters();
        }
        // It says HTTPS but works also for any TCP connection.
        // It checks SAN (Subject Alternative Name) as well as CN.
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        return sslParameters;
    }

    /**
     * The default {@link SocketConfigurator} that disables Nagle's algorithm.
     *
     * @return Default configurator: only disables Nagle's algirithm
     */
    public static SocketConfigurator defaultConfigurator() {
        return DEFAULT;
    }

    /**
     * {@link SocketConfigurator} that disables Nagle's algorithm.
     *
     * @return A composable configurator that diasbles Nagle's algirithm
     */
    public static SocketConfigurator disableNagleAlgorithm() {
        return DISABLE_NAGLE_ALGORITHM;
    }

    /**
     * {@link SocketConfigurator} that enable server hostname verification for TLS connections.
     *
     * @return A composable configurator that enables peer hostname verification
     */
    public static SocketConfigurator enableHostnameVerification() {
        return ENABLE_HOSTNAME_VERIFICATION;
    }

    /**
     * Builder to configure and creates a {@link SocketConfigurator} instance.
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SocketConfigurator configurator = socket -> {
        };

        /**
         * Set default configuration.
         *
         * @return this
         */
        public Builder defaultConfigurator() {
            configurator = configurator.andThen(DEFAULT);
            return this;
        }

        /**
         * Disable Nagle's Algorithm.
         *
         * @return this
         */
        public Builder disableNagleAlgorithm() {
            configurator = configurator.andThen(DISABLE_NAGLE_ALGORITHM);
            return this;
        }

        /**
         * Enable server hostname verification for TLS connections.
         *
         * @return this
         */
        public Builder enableHostnameVerification() {
            configurator = configurator.andThen(ENABLE_HOSTNAME_VERIFICATION);
            return this;
        }

        /**
         * Add an extra configuration step.
         *
         * @param extraConfiguration
         * @return this
         */
        public Builder add(SocketConfigurator extraConfiguration) {
            configurator = configurator.andThen(extraConfiguration);
            return this;
        }

        /**
         * Return the configured {@link SocketConfigurator}.
         *
         * @return the final configurator
         */
        public SocketConfigurator build() {
            return configurator;
        }
    }
}
