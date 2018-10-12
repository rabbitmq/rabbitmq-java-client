package com.rabbitmq.client.impl.nio;

/**
 * Contract to exchange frame between application threads and NIO thread.
 * <p>
 * This is a simplified subset of {@link java.util.concurrent.BlockingQueue}.
 * This interface is considered a SPI and is likely to move between
 * minor and patch releases.
 *
 * @see NioParams
 * @since 5.5.0
 */
public interface NioQueue {

    /**
     * Enqueue a frame, block if the queue is full.
     *
     * @param writeRequest
     * @return
     * @throws InterruptedException
     */
    boolean offer(WriteRequest writeRequest) throws InterruptedException;

    /**
     * Get the current size of the queue.
     *
     * @return
     */
    int size();

    /**
     * Retrieves and removes the head of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    WriteRequest poll();

    /**
     * Returns {@code true} if the queue contains no element.
     *
     * @return {@code true} if the queue contains no element
     */
    boolean isEmpty();
}
