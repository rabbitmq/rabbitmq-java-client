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
import java.io.IOException;
import java.net.Socket;

/**
 * Ready-to-use instances and builder for {@link SocketConfigurator}.
 * <p>
 * Note {@link SocketConfigurator}s can be combined with
 * {@link AbstractSocketConfigurator#andThen(SocketConfigurator)}.
 *
 * @since 4.8.0
 */
public abstract class SocketConfigurators {

    /**
     * Disable Nagle's algorithm.
     */
    public static final AbstractSocketConfigurator DISABLE_NAGLE_ALGORITHM = new AbstractSocketConfigurator() {

        @Override
        public void configure(Socket socket) throws IOException {
            socket.setTcpNoDelay(true);
        }
    };

    /**
     * Default {@link SocketConfigurator} that disables Nagle's algorithm.
     */
    public static final AbstractSocketConfigurator DEFAULT = DISABLE_NAGLE_ALGORITHM;

    /**
     * Enable server hostname validation for TLS connections.
     */
    public static final AbstractSocketConfigurator ENABLE_HOSTNAME_VERIFICATION = new AbstractSocketConfigurator() {

        @Override
        public void configure(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;
                SSLParameters sslParameters = enableHostnameVerification(sslSocket.getSSLParameters());
                sslSocket.setSSLParameters(sslParameters);
            }
        }
    };

    static final SSLParameters enableHostnameVerification(SSLParameters sslParameters) {
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
     * @return
     */
    public static AbstractSocketConfigurator defaultConfigurator() {
        return DEFAULT;
    }

    /**
     * {@link SocketConfigurator} that disables Nagle's algorithm.
     *
     * @return
     */
    public static AbstractSocketConfigurator disableNagleAlgorithm() {
        return DISABLE_NAGLE_ALGORITHM;
    }

    /**
     * {@link SocketConfigurator} that enable server hostname verification for TLS connections.
     *
     * @return
     */
    public static AbstractSocketConfigurator enableHostnameVerification() {
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

    public static abstract class AbstractSocketConfigurator implements SocketConfigurator {

        /**
         * Returns a composed configurator that performs, in sequence, this
         * operation followed by the {@code after} operation.
         *
         * @param after the operation to perform after this operation
         * @return a composed configurator that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        public AbstractSocketConfigurator andThen(final SocketConfigurator after) {
            if (after == null) {
                throw new NullPointerException();
            }
            return new AbstractSocketConfigurator() {

                @Override
                public void configure(Socket t) throws IOException {
                    AbstractSocketConfigurator.this.configure(t);
                    after.configure(t);
                }
            };
        }
    }

    public static class Builder {

        private AbstractSocketConfigurator configurator = new AbstractSocketConfigurator() {

            @Override
            public void configure(Socket socket) {
            }
        };

        /**
         * Set default configuration.
         *
         * @return
         */
        public Builder defaultConfigurator() {
            configurator = configurator.andThen(DEFAULT);
            return this;
        }

        /**
         * Disable Nagle's Algorithm.
         *
         * @return
         */
        public Builder disableNagleAlgorithm() {
            configurator = configurator.andThen(DISABLE_NAGLE_ALGORITHM);
            return this;
        }

        /**
         * Enable server hostname verification for TLS connections.
         *
         * @return
         */
        public Builder enableHostnameVerification() {
            configurator = configurator.andThen(ENABLE_HOSTNAME_VERIFICATION);
            return this;
        }

        /**
         * Add an extra configuration step.
         *
         * @param extraConfiguration
         * @return
         */
        public Builder add(SocketConfigurator extraConfiguration) {
            configurator = configurator.andThen(extraConfiguration);
            return this;
        }

        /**
         * Return the configured {@link SocketConfigurator}.
         *
         * @return
         */
        public SocketConfigurator build() {
            return configurator;
        }
    }
}
