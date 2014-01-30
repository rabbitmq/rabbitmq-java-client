package com.rabbitmq.client;

import java.io.IOException;
import java.net.Socket;

public class DefaultSocketConfigurator implements SocketConfigurator {
    /**
     *  Provides a hook to insert custom configuration of the sockets
     *  used to connect to an AMQP server before they connect.
     *
     *  The default behaviour of this method is to disable Nagle's
     *  algorithm to get more consistently low latency.  However it
     *  may be overridden freely and there is no requirement to retain
     *  this behaviour.
     *
     *  @param socket The socket that is to be used for the Connection
     */
    public void configure(Socket socket) throws IOException {
        // disable Nagle's algorithm, for more consistently low latency
        socket.setTcpNoDelay(true);
    }
}
