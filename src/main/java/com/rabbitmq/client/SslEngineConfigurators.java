// Copyright (c) 2018-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;

/**
 * Ready-to-use instances and builder for {@link SslEngineConfigurator}s.
 * <p>
 * Note {@link SslEngineConfigurator}s can be combined with
 * {@link AbstractSslEngineConfigurator#andThen(SslEngineConfigurator)}.
 *
 * @since 4.8.0
 */
public abstract class SslEngineConfigurators {

    /**
     * Default {@link SslEngineConfigurator}, does nothing.
     */
    public static final AbstractSslEngineConfigurator DEFAULT = new AbstractSslEngineConfigurator() {

        @Override
        public void configure(SSLEngine sslEngine) {
        }
    };

    /**
     * {@link SslEngineConfigurator} that enables server hostname verification.
     *
     * <p>
     * Requires Java 7 or more.
     *
     */
    public static final AbstractSslEngineConfigurator ENABLE_HOSTNAME_VERIFICATION = new AbstractSslEngineConfigurator() {

        @Override
        public void configure(SSLEngine sslEngine) throws IOException {
            SSLParameters sslParameters = SocketConfigurators.enableHostnameVerification(sslEngine.getSSLParameters());
            sslEngine.setSSLParameters(sslParameters);
        }
    };

    /**
     * Default {@link SslEngineConfigurator}, does nothing.
     *
     * @return
     */
    public static AbstractSslEngineConfigurator defaultConfigurator() {
        return DEFAULT;
    }

    /**
     * {@link SslEngineConfigurator} that enables server hostname verification.
     *
     * <p>
     * Requires Java 7 or more.
     *
     * @return
     */
    public static AbstractSslEngineConfigurator enableHostnameVerification() {
        return ENABLE_HOSTNAME_VERIFICATION;
    }

    /**
     * Builder to configure and creates a {@link SslEngineConfigurator} instance.
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    public static abstract class AbstractSslEngineConfigurator implements SslEngineConfigurator {


        /**
         * Returns a composed configurator that performs, in sequence, this
         * operation followed by the {@code after} operation.
         *
         * @param after the operation to perform after this operation
         * @return a composed configurator that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        public AbstractSslEngineConfigurator andThen(final SslEngineConfigurator after) {
            if (after == null) {
                throw new NullPointerException();
            }
            return new AbstractSslEngineConfigurator() {

                @Override
                public void configure(SSLEngine t) throws IOException {
                    AbstractSslEngineConfigurator.this.configure(t);
                    after.configure(t);
                }
            };
        }

    }

    public static class Builder {

        private AbstractSslEngineConfigurator configurator = new AbstractSslEngineConfigurator() {

            @Override
            public void configure(SSLEngine channel) {
            }
        };

        /**
         * Set default configuration (no op).
         *
         * @return
         */
        public Builder defaultConfigurator() {
            configurator = configurator.andThen(DEFAULT);
            return this;
        }

        /**
         * Enables server hostname verification.
         *
         * <p>
         * Requires Java 7 or more.
         *
         * @return
         */
        public Builder enableHostnameVerification() {
            configurator = configurator.andThen(ENABLE_HOSTNAME_VERIFICATION);
            return this;
        }

        /**
         * Add extra configuration step.
         *
         * @param extraConfiguration
         * @return
         */
        public Builder add(SslEngineConfigurator extraConfiguration) {
            configurator = configurator.andThen(extraConfiguration);
            return this;
        }

        /**
         * Return the configured {@link SslEngineConfigurator}.
         *
         * @return
         */
        public SslEngineConfigurator build() {
            return configurator;
        }
    }
}
