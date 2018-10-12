package com.rabbitmq.client.impl.nio;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * Default {@link ByteBufferFactory} that creates heap-based {@link ByteBuffer}s.
 * This behavior can be changed by passing in a custom {@link Function<Integer, ByteBuffer>}
 * to the constructor.
 *
 * @see NioParams
 * @see ByteBufferFactory
 * @since 5.5.0
 */
public class DefaultByteBufferFactory implements ByteBufferFactory {

    private final Function<Integer, ByteBuffer> allocator;

    public DefaultByteBufferFactory(Function<Integer, ByteBuffer> allocator) {
        this.allocator = allocator;
    }

    public DefaultByteBufferFactory() {
        this(capacity -> ByteBuffer.allocate(capacity));
    }

    @Override
    public ByteBuffer createReadBuffer(NioContext nioContext) {
        if (nioContext.getSslEngine() == null) {
            return allocator.apply(nioContext.getNioParams().getReadByteBufferSize());
        } else {
            return allocator.apply(nioContext.getSslEngine().getSession().getApplicationBufferSize());
        }
    }

    @Override
    public ByteBuffer createWriteBuffer(NioContext nioContext) {
        if (nioContext.getSslEngine() == null) {
            return allocator.apply(nioContext.getNioParams().getWriteByteBufferSize());
        } else {
            return allocator.apply(nioContext.getSslEngine().getSession().getApplicationBufferSize());
        }
    }

    @Override
    public ByteBuffer createEncryptedReadBuffer(NioContext nioContext) {
        return createEncryptedByteBuffer(nioContext);
    }

    @Override
    public ByteBuffer createEncryptedWriteBuffer(NioContext nioContext) {
        return createEncryptedByteBuffer(nioContext);
    }

    protected ByteBuffer createEncryptedByteBuffer(NioContext nioContext) {
        if (nioContext.getSslEngine() == null) {
            throw new IllegalArgumentException("Encrypted byte buffer should be created only in SSL/TLS context");
        } else {
            return allocator.apply(nioContext.getSslEngine().getSession().getPacketBufferSize());
        }
    }
}
