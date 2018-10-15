package com.rabbitmq.client.impl.nio;

/**
 * Contract to create {@link NioQueue}.
 *
 * @see NioQueue
 * @since 4.9.0
 */
public interface NioQueueFactory {

    /**
     * Create a {@link NioQueue} instance
     *
     * @param nioContext
     * @return
     */
    NioQueue create(NioContext nioContext);
}
