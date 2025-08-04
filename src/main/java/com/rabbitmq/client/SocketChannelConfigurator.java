// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 *
 * @deprecated Use {@link ConnectionFactory#netty()} with a channel customizer instead.
 */
@FunctionalInterface
@Deprecated
public interface SocketChannelConfigurator {

    /**
     * Provides a hook to insert custom configuration of the {@link SocketChannel}s
     * used to connect to an AMQP server before they connect.
     */
    void configure(SocketChannel socketChannel) throws IOException;

    /**
     * Returns a composed configurator that performs, in sequence, this
     * operation followed by the {@code after} operation.
     *
     * @param after the operation to perform after this operation
     * @return a composed configurator that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default SocketChannelConfigurator andThen(SocketChannelConfigurator after) {
        Objects.requireNonNull(after);
        return t -> { configure(t); after.configure(t); };
    }

}
