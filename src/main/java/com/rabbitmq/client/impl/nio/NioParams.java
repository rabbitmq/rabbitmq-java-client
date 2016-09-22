// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.impl.nio;

import com.rabbitmq.client.DefaultSocketChannelConfigurator;
import com.rabbitmq.client.SocketChannelConfigurator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Parameters used to configure the NIO mode of a {@link com.rabbitmq.client.ConnectionFactory}.
 *
 */
public class NioParams {

    /** size of the byte buffer used for inbound data */
    private int readByteBufferSize = 8192;

    /** size of the byte buffer used for outbound data */
    private int writeByteBufferSize = 8192;

    /** the max number of IO threads */
    private int nbIoThreads = 1;

    /** the timeout to enqueue outbound frames */
    private int writeEnqueuingTimeoutInMs = 10 * 1000;

    /** the capacity of the queue used for outbound frames */
    private int writeQueueCapacity = 10000;

    /** the executor service used for IO threads and connections shutdown */
    private ExecutorService nioExecutor;

    /** the thread factory used for IO threads and connections shutdown */
    private ThreadFactory threadFactory;

    /** the hook to configure the socket channel before it's open */
    private SocketChannelConfigurator socketChannelConfigurator = new DefaultSocketChannelConfigurator();

    public NioParams() {
    }

    public NioParams(NioParams nioParams) {
        setReadByteBufferSize(nioParams.getReadByteBufferSize());
        setWriteByteBufferSize(nioParams.getWriteByteBufferSize());
        setNbIoThreads(nioParams.getNbIoThreads());
        setWriteEnqueuingTimeoutInMs(nioParams.getWriteEnqueuingTimeoutInMs());
        setWriteQueueCapacity(nioParams.getWriteQueueCapacity());
        setNioExecutor(nioParams.getNioExecutor());
        setThreadFactory(nioParams.getThreadFactory());
    }

    public int getReadByteBufferSize() {
        return readByteBufferSize;
    }

    /**
     * Sets the size in byte of the read {@link java.nio.ByteBuffer} used in the NIO loop.
     * Default is 8192.
     *
     * This parameter isn't used when using SSL/TLS, where {@link java.nio.ByteBuffer}
     * size is set up according to the {@link javax.net.ssl.SSLSession} packet size.
     *
     * @param readByteBufferSize size of the {@link java.nio.ByteBuffer} for inbound data
     * @return this {@link NioParams} instance
     */
    public NioParams setReadByteBufferSize(int readByteBufferSize) {
        if (readByteBufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than 0");
        }
        this.readByteBufferSize = readByteBufferSize;
        return this;
    }

    public int getWriteByteBufferSize() {
        return writeByteBufferSize;
    }

    /**
     * Sets the size in byte of the write {@link java.nio.ByteBuffer} used in the NIO loop.
     * Default is 8192.
     *
     * This parameter isn't used when using SSL/TLS, where {@link java.nio.ByteBuffer}
     * size is set up according to the {@link javax.net.ssl.SSLSession} packet size.
     *
     * @param writeByteBufferSize size of the {@link java.nio.ByteBuffer} used for outbound data
     * @return this {@link NioParams} instance
     */
    public NioParams setWriteByteBufferSize(int writeByteBufferSize) {
        if (readByteBufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than 0");
        }
        this.writeByteBufferSize = writeByteBufferSize;
        return this;
    }

    public int getNbIoThreads() {
        return nbIoThreads;
    }

    /**
     * Sets the max number of threads/tasks used for NIO. Default is 1.
     * Set this number according to the number of simultaneous connections
     * and their activity.
     * Threads/tasks are created as necessary (e.g. with 10 threads, when
     * 10 connections have been created).
     * Once a connection is created, it's assigned to a thread/task and
     * all its IO activity is handled by this thread/task.
     *
     * When idle for a few seconds (i.e. without any connection to perform IO for),
     * a thread/task stops and is recreated if necessary.
     *
     * @param nbIoThreads
     * @return this {@link NioParams} instance
     */
    public NioParams setNbIoThreads(int nbIoThreads) {
        if (nbIoThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be greater than 0");
        }
        this.nbIoThreads = nbIoThreads;
        return this;
    }

    public int getWriteEnqueuingTimeoutInMs() {
        return writeEnqueuingTimeoutInMs;
    }

    /**
     * Sets the timeout for queuing outbound frames. Default is 10,000 ms.
     * Every requests to the server is divided into frames
     * that are then queued in a {@link java.util.concurrent.BlockingQueue} before
     * being sent on the network by a IO thread.
     *
     * If the IO thread cannot cope with the frames dispatch, the
     * {@link java.util.concurrent.BlockingQueue} gets filled up and blocks
     * (blocking the calling thread by the same occasion). This timeout is the
     * time the {@link java.util.concurrent.BlockingQueue} will wait before
     * rejecting the outbound frame. The calling thread will then received
     * an exception.
     *
     * The appropriate value depends on the application scenarios:
     * rate of outbound data (published messages, acknowledgment, etc), network speed...
     *
     * @param writeEnqueuingTimeoutInMs
     * @return this {@link NioParams} instance
     * @see NioParams#setWriteQueueCapacity(int)
     */
    public NioParams setWriteEnqueuingTimeoutInMs(int writeEnqueuingTimeoutInMs) {
        this.writeEnqueuingTimeoutInMs = writeEnqueuingTimeoutInMs;
        return this;
    }

    public ExecutorService getNioExecutor() {
        return nioExecutor;
    }

    /**
     * Sets the {@link ExecutorService} to use for NIO threads/tasks.
     * Default is to use the thread factory.
     *
     * The {@link ExecutorService} should be able to run the
     * number of requested IO threads, plus a few more, as it's also
     * used to dispatch the shutdown of connections.
     *
     * It's developer's responsibility to shut down the executor
     * when it is no longer needed.
     *
     * The thread factory isn't used if an executor service is set up.
     *
     * @param nioExecutor {@link ExecutorService} used for IO threads and connection shutdown
     * @return this {@link NioParams} instance
     * @see NioParams#setNbIoThreads(int)
     * @see NioParams#setThreadFactory(ThreadFactory)
     */
    public NioParams setNioExecutor(ExecutorService nioExecutor) {
        this.nioExecutor = nioExecutor;
        return this;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets the {@link ThreadFactory} to use for NIO threads/tasks.
     * Default is to use the {@link com.rabbitmq.client.ConnectionFactory}'s
     * {@link ThreadFactory}.
     *
     * The {@link ThreadFactory} is used to spawn the IO threads
     * and dispatch the shutdown of connections.
     *
     * @param threadFactory {@link ThreadFactory} used for IO threads and connection shutdown
     * @return this {@link NioParams} instance
     * @see NioParams#setNbIoThreads(int)
     * @see NioParams#setNioExecutor(ExecutorService)
     */
    public NioParams setThreadFactory(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    public int getWriteQueueCapacity() {
        return writeQueueCapacity;
    }

    /**
     * Set the capacity of the queue used for outbound frames.
     * Default capacity is 10,000.
     *
     * @param writeQueueCapacity
     * @return this {@link NioParams} instance
     * @see NioParams#setWriteEnqueuingTimeoutInMs(int)
     */
    public NioParams setWriteQueueCapacity(int writeQueueCapacity) {
        if (writeQueueCapacity <= 0) {
            throw new IllegalArgumentException("Write queue capacity must be greater than 0");
        }
        this.writeQueueCapacity = writeQueueCapacity;
        return this;
    }

    /**
     * Set the {@link java.nio.channels.SocketChannel} configurator.
     * This gets a chance to "configure" a socket channel
     * before it has been opened. The default implementation disables
     * Nagle's algorithm.
     *
     * @param configurator the configurator to use
     */
    public void setSocketChannelConfigurator(SocketChannelConfigurator configurator) {
        this.socketChannelConfigurator = configurator;
    }

    public SocketChannelConfigurator getSocketChannelConfigurator() {
        return socketChannelConfigurator;
    }
}
