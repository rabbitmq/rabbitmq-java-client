// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurator;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class FrameHandlerFactory {
    private final int connectionTimeout;
    private final SocketFactory factory;
    protected final SocketConfigurator configurator;
    private final ExecutorService shutdownExecutor;
    protected final boolean ssl;

    public FrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl) {
        this(connectionTimeout, factory, configurator, ssl, null);
    }

    public FrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl, ExecutorService shutdownExecutor) {
        this.connectionTimeout = connectionTimeout;
        this.factory = factory;
        this.configurator = configurator;
        this.ssl = ssl;
        this.shutdownExecutor = shutdownExecutor;
    }

    public FrameHandler create(Address addr) throws IOException {
        String hostName = addr.getHost();
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);
        Socket socket = null;
        try {
            socket = factory.createSocket();
            configurator.configure(socket);
            socket.connect(new InetSocketAddress(hostName, portNumber),
                    connectionTimeout);
            return create(socket);
        } catch (IOException ioe) {
            quietTrySocketClose(socket);
            throw ioe;
        }
    }

    public FrameHandler create(Socket sock) throws IOException
    {
        return new SocketFrameHandler(sock, this.shutdownExecutor);
    }

    private static void quietTrySocketClose(Socket socket) {
        if (socket != null)
            try { socket.close(); } catch (Exception _e) {/*ignore exceptions*/}
    }
}
