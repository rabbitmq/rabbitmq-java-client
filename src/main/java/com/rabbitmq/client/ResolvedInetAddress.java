package com.rabbitmq.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ResolvedInetAddress extends Address {
    private final InetAddress inetAddress;

    public ResolvedInetAddress(InetAddress inetAddress, int port) {
        super(inetAddress.getHostAddress(), port);
        this.inetAddress = inetAddress;
    }

    public ResolvedInetAddress(InetAddress inetAddress) {
        super(inetAddress.getHostAddress());
        this.inetAddress = inetAddress;
    }

    @Override
    public InetSocketAddress toInetSocketAddress(int port) {
        return new InetSocketAddress(inetAddress, port);
    }
}
