package com.rabbitmq.client.impl.nio;

import java.nio.ByteBuffer;

/**
 * Default {@link ByteBufferFactory} that creates heap-based {@link ByteBuffer}s.
 * This behavior can be changed by passing in a custom {@link ByteBufferAllocator}
 * to the constructor.
 *
 * @see NioParams
 * @see ByteBufferFactory
 * @since 4.9.0
 */
public class DefaultByteBufferFactory implements ByteBufferFactory {

    private final ByteBufferAllocator allocator;

    public DefaultByteBufferFactory(ByteBufferAllocator allocator) {
        this.allocator = allocator;
    }

    public DefaultByteBufferFactory() {
        this(new ByteBufferAllocator() {

            @Override
            public ByteBuffer allocate(int capacity) {
                return ByteBuffer.allocate(capacity);
            }
        });
    }

    @Override
    public ByteBuffer createReadBuffer(NioContext nioContext) {
        if (nioContext.getSslEngine() == null) {
            return allocator.allocate(nioContext.getNioParams().getReadByteBufferSize());
        } else {
            return allocator.allocate(nioContext.getSslEngine().getSession().getApplicationBufferSize());
        }
    }

    @Override
    public ByteBuffer createWriteBuffer(NioContext nioContext) {
        if (nioContext.getSslEngine() == null) {
            return allocator.allocate(nioContext.getNioParams().getWriteByteBufferSize());
        } else {
            return allocator.allocate(nioContext.getSslEngine().getSession().getApplicationBufferSize());
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
            return allocator.allocate(nioContext.getSslEngine().getSession().getPacketBufferSize());
        }
    }

    public interface ByteBufferAllocator {

        ByteBuffer allocate(int capacity);
    }
}
