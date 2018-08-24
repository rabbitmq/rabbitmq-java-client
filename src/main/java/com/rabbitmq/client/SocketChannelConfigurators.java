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

/**
 * Ready-to-use instances and builder for {@link SocketChannelConfigurator}.
 * <p>
 * Note {@link SocketChannelConfigurator}s can be combined with
 * {@link SocketChannelConfigurator#andThen(SocketChannelConfigurator)}.
 *
 * @since 5.4.0
 */
public abstract class SocketChannelConfigurators {

    /**
     * Disable Nagle's algorithm.
     */
    public static final SocketChannelConfigurator DISABLE_NAGLE_ALGORITHM =
        socketChannel -> SocketConfigurators.DISABLE_NAGLE_ALGORITHM.configure(socketChannel.socket());

    /**
     * Default {@link SocketChannelConfigurator} that disables Nagle's algorithm.
     */
    public static final SocketChannelConfigurator DEFAULT = DISABLE_NAGLE_ALGORITHM;

    /**
     * The default {@link SocketChannelConfigurator} that disables Nagle's algorithm.
     *
     * @return
     */
    public static SocketChannelConfigurator defaultConfigurator() {
        return DEFAULT;
    }

    /**
     * {@link SocketChannelConfigurator} that disables Nagle's algorithm.
     *
     * @return
     */
    public static SocketChannelConfigurator disableNagleAlgorithm() {
        return DISABLE_NAGLE_ALGORITHM;
    }

    /**
     * Builder to configure and creates a {@link SocketChannelConfigurator} instance.
     *
     * @return
     */
    public static SocketChannelConfigurators.Builder builder() {
        return new SocketChannelConfigurators.Builder();
    }

    public static class Builder {

        private SocketChannelConfigurator configurator = channel -> {
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
         * Add an extra configuration step.
         *
         * @param extraConfiguration
         * @return
         */
        public Builder add(SocketChannelConfigurator extraConfiguration) {
            configurator = configurator.andThen(extraConfiguration);
            return this;
        }

        /**
         * Return the configured {@link SocketConfigurator}.
         *
         * @return
         */
        public SocketChannelConfigurator build() {
            return configurator;
        }
    }
}
