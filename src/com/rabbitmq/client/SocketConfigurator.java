package com.rabbitmq.client;

import java.io.IOException;
import java.net.Socket;

public interface SocketConfigurator {
    /**
     * Provides a hook to insert custom configuration of the sockets
     * used to connect to an AMQP server before they connect.
     */
    void configure(Socket socket) throws IOException;
}
