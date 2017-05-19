// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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

import javax.net.ssl.SSLContext;

/**
 * A factory to create {@link SSLContext}s.
 *
 * @see ConnectionFactory#setSslContextFactory(SslContextFactory)
 * @since 5.0.0
 */
public interface SslContextFactory {

    /**
     * Create a {@link SSLContext} for a given name.
     * The name is typically the name of the connection.
     * @param name name of the connection the SSLContext is used for
     * @return the SSLContext for this name
     * @see ConnectionFactory#newConnection(String)
     */
    SSLContext create(String name);

}
