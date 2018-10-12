package com.rabbitmq.client.impl.nio;

import javax.net.ssl.SSLEngine;

/**
 * Context when creating resources for a NIO-based connection.
 *
 * @see ByteBufferFactory
 * @since 5.5.0
 */
public class NioContext {

    private final NioParams nioParams;

    private final SSLEngine sslEngine;

    NioContext(NioParams nioParams, SSLEngine sslEngine) {
        this.nioParams = nioParams;
        this.sslEngine = sslEngine;
    }

    /**
     * NIO params.
     *
     * @return
     */
    public NioParams getNioParams() {
        return nioParams;
    }

    /**
     * {@link SSLEngine} for SSL/TLS connection.
     * Null for plain connection.
     *
     * @return
     */
    public SSLEngine getSslEngine() {
        return sslEngine;
    }
}
