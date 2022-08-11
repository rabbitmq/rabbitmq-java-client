package com.rabbitmq.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ResolvedInetAddress extends Address {
    private final InetAddress inetAddress;

    public ResolvedInetAddress(String originalHostname, InetAddress inetAddress, int port) {
        super(originalHostname, port);
        this.inetAddress = inetAddress;
    }

    @Override
    public InetSocketAddress toInetSocketAddress(int port) {
        return new InetSocketAddress(inetAddress, port);
    }
}
