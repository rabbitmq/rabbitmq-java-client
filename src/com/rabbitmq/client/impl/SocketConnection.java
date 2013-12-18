package com.rabbitmq.client.impl;

import java.net.InetAddress;

public interface SocketConnection {

    /**
     * Retrieve the local host.
     * @return the client socket address.
     */
    InetAddress getLocalAddress();

    /**
     * Retrieve the local port number.
     * @return the client socket port number
     */
    int getLocalPort();

    /**
     * @return connection name as used by rabbitmqctl and management UI.
     */
    String getName();
}
