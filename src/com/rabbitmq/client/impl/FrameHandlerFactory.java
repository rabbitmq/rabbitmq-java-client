package com.rabbitmq.client.impl;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurator;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FrameHandlerFactory {
    private int connectionTimeout;
    private SocketFactory factory;
    private SocketConfigurator configurator;
    private boolean ssl;

    public FrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl) {
        this.connectionTimeout = connectionTimeout;
        this.factory = factory;
        this.configurator = configurator;
        this.ssl = ssl;
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
        return new SocketFrameHandler(sock);
    }

    private static void quietTrySocketClose(Socket socket) {
        if (socket != null)
            try { socket.close(); } catch (Exception _e) {/*ignore exceptions*/}
    }
}
