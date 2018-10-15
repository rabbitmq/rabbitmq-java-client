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

import com.rabbitmq.client.SocketChannelConfigurator;
import com.rabbitmq.client.SocketChannelConfigurators;
import com.rabbitmq.client.SslEngineConfigurator;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

import static com.rabbitmq.client.SslEngineConfigurators.ENABLE_HOSTNAME_VERIFICATION;

/**
 * Parameters used to configure the NIO mode of a {@link com.rabbitmq.client.ConnectionFactory}.
 *
 * @since 4.0.0
 */
public class NioParams {

    static Function<NioContext, NioQueue> DEFAULT_WRITE_QUEUE_FACTORY =
        ctx -> new BlockingQueueNioQueue(
            new ArrayBlockingQueue<>(ctx.getNioParams().getWriteQueueCapacity(), true),
            ctx.getNioParams().getWriteEnqueuingTimeoutInMs()
        );

    /**
     * size of the byte buffer used for inbound data
     */
    private int readByteBufferSize = 32768;

    /**
     * size of the byte buffer used for outbound data
     */
    private int writeByteBufferSize = 32768;

    /**
     * the max number of IO threads
     */
    private int nbIoThreads = 1;

    /**
     * the timeout to enqueue outbound frames
     */
    private int writeEnqueuingTimeoutInMs = 10 * 1000;

    /**
     * the capacity of the queue used for outbound frames
     */
    private int writeQueueCapacity = 10000;

    /**
     * the executor service used for IO threads and connections shutdown
     */
    private ExecutorService nioExecutor;

    /**
     * the thread factory used for IO threads and connections shutdown
     */
    private ThreadFactory threadFactory;

    /**
     * the hook to configure the socket channel before it's open
     */
    private SocketChannelConfigurator socketChannelConfigurator = SocketChannelConfigurators.defaultConfigurator();

    /**
     * the hook to configure the SSL engine before the connection is open
     */
    private SslEngineConfigurator sslEngineConfigurator = sslEngine -> {
    };

    /**
     * the executor service used for connection shutdown
     *
     * @since 5.4.0
     */
    private ExecutorService connectionShutdownExecutor;

    /**
     * The factory to create {@link java.nio.ByteBuffer}s.
     * The default is to create heap-based {@link java.nio.ByteBuffer}s.
     *
     * @since 5.5.0
     */
    private ByteBufferFactory byteBufferFactory = new DefaultByteBufferFactory();

    /**
     * Factory to create a {@link NioQueue}.
     *
     * @since 5.5.0
     */
    private Function<NioContext, NioQueue> writeQueueFactory =
        DEFAULT_WRITE_QUEUE_FACTORY;

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
        setSocketChannelConfigurator(nioParams.getSocketChannelConfigurator());
        setSslEngineConfigurator(nioParams.getSslEngineConfigurator());
        setConnectionShutdownExecutor(nioParams.getConnectionShutdownExecutor());
        setByteBufferFactory(nioParams.getByteBufferFactory());
        setWriteQueueFactory(nioParams.getWriteQueueFactory());
    }

    /**
     * Enable server hostname verification for TLS connections.
     *
     * @return this {@link NioParams} instance
     * @see NioParams#setSslEngineConfigurator(SslEngineConfigurator)
     * @see com.rabbitmq.client.SslEngineConfigurators#ENABLE_HOSTNAME_VERIFICATION
     */
    public NioParams enableHostnameVerification() {
        if (this.sslEngineConfigurator == null) {
            this.sslEngineConfigurator = ENABLE_HOSTNAME_VERIFICATION;
        } else {
            this.sslEngineConfigurator = this.sslEngineConfigurator.andThen(ENABLE_HOSTNAME_VERIFICATION);
        }
        return this;
    }

    public int getReadByteBufferSize() {
        return readByteBufferSize;
    }

    /**
     * Sets the size in byte of the read {@link java.nio.ByteBuffer} used in the NIO loop.
     * Default is 32768.
     * <p>
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
     * Default is 32768.
     * <p>
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
     * <p>
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
     * <p>
     * If the IO thread cannot cope with the frames dispatch, the
     * {@link java.util.concurrent.BlockingQueue} gets filled up and blocks
     * (blocking the calling thread by the same occasion). This timeout is the
     * time the {@link java.util.concurrent.BlockingQueue} will wait before
     * rejecting the outbound frame. The calling thread will then received
     * an exception.
     * <p>
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
     * <p>
     * The {@link ExecutorService} should be able to run the
     * number of requested IO threads, plus a few more, as it's also
     * used to dispatch the shutdown of connections.
     * <p>
     * Connection shutdown can also be handled by a dedicated {@link ExecutorService},
     * see {@link #setConnectionShutdownExecutor(ExecutorService)}.
     * <p>
     * It's developer's responsibility to shut down the executor
     * when it is no longer needed.
     * <p>
     * The thread factory isn't used if an executor service is set up.
     *
     * @param nioExecutor {@link ExecutorService} used for IO threads and connection shutdown
     * @return this {@link NioParams} instance
     * @see NioParams#setNbIoThreads(int)
     * @see NioParams#setThreadFactory(ThreadFactory)
     * @see NioParams#setConnectionShutdownExecutor(ExecutorService)
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
     * <p>
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

    public SocketChannelConfigurator getSocketChannelConfigurator() {
        return socketChannelConfigurator;
    }

    /**
     * Set the {@link java.nio.channels.SocketChannel} configurator.
     * This gets a chance to "configure" a socket channel
     * before it has been opened. The default implementation disables
     * Nagle's algorithm.
     *
     * @param configurator the configurator to use
     * @return this {@link NioParams} instance
     */
    public NioParams setSocketChannelConfigurator(SocketChannelConfigurator configurator) {
        this.socketChannelConfigurator = configurator;
        return this;
    }

    public SslEngineConfigurator getSslEngineConfigurator() {
        return sslEngineConfigurator;
    }

    /**
     * Set the {@link SSLEngine} configurator.
     * This gets a change to "configure" the SSL engine
     * before the connection has been opened. This can be
     * used e.g. to set {@link javax.net.ssl.SSLParameters}.
     * The default implementation doesn't do anything.
     *
     * @param configurator the configurator to use
     * @return this {@link NioParams} instance
     */
    public NioParams setSslEngineConfigurator(SslEngineConfigurator configurator) {
        this.sslEngineConfigurator = configurator;
        return this;
    }

    public ExecutorService getConnectionShutdownExecutor() {
        return connectionShutdownExecutor;
    }

    /**
     * Set the {@link ExecutorService} used for connection shutdown.
     * If not set, falls back to the NIO executor and then the thread factory.
     * This executor service is useful when strict control of the number of threads
     * is necessary, the application can experience the closing of several connections
     * at once, and automatic recovery is enabled. In such cases, the connection recovery
     * can take place in the same pool of threads as the NIO operations, which can
     * create deadlocks (all the threads of the pool are busy recovering, and there's no
     * thread left for NIO, so connections never recover).
     * <p>
     * Note it's developer's responsibility to shut down the executor
     * when it is no longer needed.
     * <p>
     * Using the thread factory for such scenarios avoid the deadlocks, at the price
     * of potentially creating many short-lived threads in case of massive connection lost.
     * <p>
     * With both the NIO and connection shutdown executor services set and configured
     * accordingly, the application can control reliably the number of threads used.
     *
     * @param connectionShutdownExecutor the executor service to use
     * @return this {@link NioParams} instance
     * @see NioParams#setNioExecutor(ExecutorService)
     * @since 5.4.0
     */
    public NioParams setConnectionShutdownExecutor(ExecutorService connectionShutdownExecutor) {
        this.connectionShutdownExecutor = connectionShutdownExecutor;
        return this;
    }

    /**
     * Set the factory to create {@link java.nio.ByteBuffer}s.
     * <p>
     * The default implementation creates heap-based {@link java.nio.ByteBuffer}s.
     *
     * @param byteBufferFactory the factory to use
     * @return this {@link NioParams} instance
     * @see ByteBufferFactory
     * @see DefaultByteBufferFactory
     * @since 5.5.0
     */
    public NioParams setByteBufferFactory(ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
        return this;
    }

    public ByteBufferFactory getByteBufferFactory() {
        return byteBufferFactory;
    }

    /**
     * Set the factory to create {@link NioQueue}s.
     * <p>
     * The default uses a {@link ArrayBlockingQueue}.
     *
     * @param writeQueueFactory the factory to use
     * @return this {@link NioParams} instance
     * @see NioQueue
     * @since 5.5.0
     */
    public NioParams setWriteQueueFactory(
        Function<NioContext, NioQueue> writeQueueFactory) {
        this.writeQueueFactory = writeQueueFactory;
        return this;
    }

    public Function<NioContext, NioQueue> getWriteQueueFactory() {
        return writeQueueFactory;
    }
}
