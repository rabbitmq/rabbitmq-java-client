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

import javax.net.ssl.SSLSession;
import java.net.Socket;

/**
 * The context of the freshly open TCP connection.
 *
 * @see ConnectionPostProcessor
 * @since 4.8.0
 */
public class ConnectionContext {

    private final Socket socket;
    private final Address address;
    private final boolean ssl;
    private final SSLSession sslSession;

    public ConnectionContext(Socket socket, Address address, boolean ssl, SSLSession sslSession) {
        this.socket = socket;
        this.address = address;
        this.ssl = ssl;
        this.sslSession = sslSession;
    }

    /**
     * The network socket. Can be an {@link javax.net.ssl.SSLSocket}.
     *
     * @return
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * The address (hostname and port) used for connecting.
     *
     * @return
     */
    public Address getAddress() {
        return address;
    }

    /**
     * Whether this is a SSL/TLS connection.
     * @return
     */
    public boolean isSsl() {
        return ssl;
    }

    /**
     * The {@link SSLSession} for a TLS connection, <code>null</code> otherwise.
     * @return
     */
    public SSLSession getSslSession() {
        return sslSession;
    }
}
