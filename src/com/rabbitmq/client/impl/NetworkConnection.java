package com.rabbitmq.client.impl;

import java.net.InetAddress;

public interface NetworkConnection {

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

    /** Retrieve address of peer. */
    InetAddress getAddress();

    /** Retrieve port number of peer. */
    int getPort();
}
