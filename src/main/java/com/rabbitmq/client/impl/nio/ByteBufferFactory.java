package com.rabbitmq.client.impl.nio;

import java.nio.ByteBuffer;

/**
 * Contract to create {@link ByteBuffer}s.
 *
 * @see NioParams
 * @since 5.5.0
 */
public interface ByteBufferFactory {

    /**
     * Create the {@link ByteBuffer} that contains inbound frames.
     * This buffer is the network buffer for plain connections.
     * When using SSL/TLS, this buffer isn't directly connected to
     * the network, the encrypted read buffer is.
     *
     * @param nioContext
     * @return
     */
    ByteBuffer createReadBuffer(NioContext nioContext);

    /**
     * Create the {@link ByteBuffer} that contains outbound frames.
     * This buffer is the network buffer for plain connections.
     * When using SSL/TLS, this buffer isn't directed connected to
     * the network, the encrypted write buffer is.
     *
     * @param nioContext
     * @return
     */
    ByteBuffer createWriteBuffer(NioContext nioContext);

    /**
     * Create the network read {@link ByteBuffer}.
     * This buffer contains encrypted frames read from the network.
     * The {@link javax.net.ssl.SSLEngine} decrypts frame and pass them
     * over to the read buffer.
     *
     * @param nioContext
     * @return
     */
    ByteBuffer createEncryptedReadBuffer(NioContext nioContext);

    /**
     * Create the network write {@link ByteBuffer}.
     * This buffer contains encrypted outbound frames. These
     * frames come from the write buffer that sends them through
     * the {@link javax.net.ssl.SSLContext} for encryption to
     * this buffer.
     *
     * @param nioContext
     * @return
     */
    ByteBuffer createEncryptedWriteBuffer(NioContext nioContext);
}
