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

/**
 * Ready-to-use instances and builder for {@link SslEngineConfigurator}s.
 * <p>
 * Note {@link SslEngineConfigurator}s can be combined with
 * {@link SslEngineConfigurator#andThen(SslEngineConfigurator)}.
 *
 * @since 5.4.0
 */
public abstract class SslEngineConfigurators {

    /**
     * Default {@link SslEngineConfigurator}, does nothing.
     */
    public static final SslEngineConfigurator DEFAULT = sslEngine -> {
    };

    /**
     * {@link SslEngineConfigurator} that enables server hostname verification.
     */
    public static final SslEngineConfigurator ENABLE_HOSTNAME_VERIFICATION = sslEngine -> {
        SSLParameters sslParameters = SocketConfigurators.enableHostnameVerification(sslEngine.getSSLParameters());
        sslEngine.setSSLParameters(sslParameters);
    };

    /**
     * Default {@link SslEngineConfigurator}, does nothing.
     *
     * @return
     */
    public static SslEngineConfigurator defaultConfigurator() {
        return DEFAULT;
    }

    /**
     * {@link SslEngineConfigurator} that enables server hostname verification.
     *
     * @return
     */
    public static SslEngineConfigurator enableHostnameVerification() {
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

    public static class Builder {

        private SslEngineConfigurator configurator = channel -> {
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
