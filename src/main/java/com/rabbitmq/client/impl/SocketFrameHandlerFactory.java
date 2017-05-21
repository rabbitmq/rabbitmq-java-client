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
import com.rabbitmq.client.SslContextFactory;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class SocketFrameHandlerFactory extends AbstractFrameHandlerFactory {

    private final SocketFactory socketFactory;
    private final ExecutorService shutdownExecutor;
    private final SslContextFactory sslContextFactory;

    public SocketFrameHandlerFactory(int connectionTimeout, SocketFactory socketFactory, SocketConfigurator configurator,
                                     boolean ssl) {
        this(connectionTimeout, socketFactory, configurator, ssl, null);
    }

    public SocketFrameHandlerFactory(int connectionTimeout, SocketFactory socketFactory, SocketConfigurator configurator,
                                     boolean ssl, ExecutorService shutdownExecutor) {
        this(connectionTimeout, socketFactory, configurator, ssl, shutdownExecutor, null);
    }

    public SocketFrameHandlerFactory(int connectionTimeout, SocketFactory socketFactory, SocketConfigurator configurator,
                                     boolean ssl, ExecutorService shutdownExecutor, SslContextFactory sslContextFactory) {
        super(connectionTimeout, configurator, ssl);
        this.socketFactory = socketFactory;
        this.shutdownExecutor = shutdownExecutor;
        this.sslContextFactory = sslContextFactory;
    }

    public FrameHandler create(Address addr, String connectionName) throws IOException {
        String hostName = addr.getHost();
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);
        Socket socket = null;
        try {
            socket = createSocket(connectionName);
            configurator.configure(socket);
            socket.connect(new InetSocketAddress(hostName, portNumber),
                    connectionTimeout);
            return create(socket);
        } catch (IOException ioe) {
            quietTrySocketClose(socket);
            throw ioe;
        }
    }

    protected Socket createSocket(String connectionName) throws IOException {
        // SocketFactory takes precedence if specified
        if (socketFactory != null) {
            return socketFactory.createSocket();
        } else {
            if (ssl) {
                return sslContextFactory.create(connectionName).getSocketFactory().createSocket();
            } else {
                return SocketFactory.getDefault().createSocket();
            }
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
