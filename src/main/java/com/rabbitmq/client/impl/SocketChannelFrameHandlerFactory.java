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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends AbstractFrameHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerFactory.class);

    private SelectorState readSelectorState;
    private SelectorState writeSelectorState;

    private final ExecutorService executorService;

    private final ThreadFactory threadFactory;

    private final Lock stateLock = new ReentrantLock();

    private final AtomicLong connectionCount = new AtomicLong();

    private Thread readThread, writeThread;

    private Future<?> writeTask;

    // FIXME make the following configuration settings
    //  size of byte buffers
    //  nb of NIO threads (should be even, 1 thread for read, 1 thread for write to scale IO
    //  SocketChannelFrameHandlerState.write timeout

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, boolean ssl, ExecutorService executorService) throws IOException {
        super(connectionTimeout, configurator, ssl);
        this.executorService = executorService;
        this.threadFactory = null;
    }

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, boolean ssl, ThreadFactory threadFactory) throws IOException {
        super(connectionTimeout, configurator, ssl);
        this.executorService = null;
        this.threadFactory = threadFactory;
    }

    @Override
    public FrameHandler create(Address addr) throws IOException {
        int portNumber = ConnectionFactory.portOrDefault(addr.getPort(), ssl);

        SocketAddress address = new InetSocketAddress(addr.getHost(), portNumber);
        SocketChannel channel = SocketChannel.open();
        configurator.configure(channel.socket());

        // lock
        stateLock.lock();
        try {
            connectionCount.incrementAndGet();
            initStateIfNecessary();
        } finally {
            stateLock.unlock();
        }

        // FIXME handle connection failure
        channel.connect(address);

        channel.configureBlocking(false);

        SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(channel, readSelectorState, writeSelectorState);

        SocketChannelFrameHandler frameHandler = new SocketChannelFrameHandler(state);
        return frameHandler;
    }

    protected boolean cleanUp() {
        long connectionCountNow = connectionCount.get();
        stateLock.lock();
        try {
            if(connectionCountNow != connectionCount.get()) {
                // a connection request has come in meanwhile, don't do anything
                return false;
            }

            if(this.executorService == null) {
                this.writeThread.interrupt();
            } else {
                boolean canceled = this.writeTask.cancel(true);
                if(!canceled) {
                    LOGGER.info("Could not stop write NIO task");
                }
            }

            try {
                readSelectorState.selector.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close read selector: {}", e.getMessage());
            }
            try {
                writeSelectorState.selector.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close write selector: {}", e.getMessage());
            }

            this.readSelectorState = null;
            this.writeSelectorState = null;

        } finally {
            stateLock.unlock();
        }
        return true;
    }

    protected void initStateIfNecessary() throws IOException {
        if(this.readSelectorState == null) {
            this.readSelectorState = new SelectorState(Selector.open());
            this.writeSelectorState = new SelectorState(Selector.open());

            startIoLoops();
        }
    }

    protected void startIoLoops() {
        if(executorService == null) {
            this.readThread = Environment.newThread(threadFactory, new ReadLoop(this.readSelectorState), "rabbitmq-nio-read");
            this.writeThread = Environment.newThread(threadFactory, new WriteLoop(this.writeSelectorState), "rabbitmq-nio-write");
            readThread.start();
            writeThread.start();
        } else {
            this.executorService.submit(new ReadLoop(this.readSelectorState));
            this.writeTask = this.executorService.submit(new WriteLoop(this.writeSelectorState));
        }
    }

    private class ReadLoop implements Runnable {

        private final SelectorState state;

        public ReadLoop(SelectorState readSelectorState) {
            this.state = readSelectorState;
        }

        @Override
        public void run() {
            Selector selector = state.selector;
            // FIXME find a better default?
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            try {
                int idlenessCount = 0;
                while(true && !Thread.currentThread().isInterrupted()) {

                    for (SelectionKey selectionKey : selector.keys()) {
                        SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) selectionKey.attachment();
                        if(state.getConnection().getHeartbeat() > 0) {
                            long now = System.currentTimeMillis();
                            if((now - state.getLastActivity()) > state.getConnection().getHeartbeat() * 1000 * 2) {
                                try {
                                    state.getConnection().handleHeartbeatFailure();
                                } catch(Exception e) {
                                    LOGGER.warn("Error after heartbeat failure of connection {}", state.getConnection());
                                } finally {
                                    selectionKey.cancel();
                                }
                            }
                        }
                    }

                    int select;
                    if(state.statesToBeRegistered.isEmpty()) {
                        // we can block, registration will call Selector.wakeup()
                        select = selector.select(1000);
                        idlenessCount++;
                        if(idlenessCount == 10 && selector.keys().size() == 0) {
                        //if(false) {
                            // we haven't been doing anything for a while, shutdown state
                            boolean clean = cleanUp();
                            if(clean) {
                                // we stop this thread
                                return;
                            }
                            // there may be incoming connections, keep going
                        }
                    } else {
                        // we don't have to block, we need to select and clean cancelled keys before registration
                        select = selector.selectNow();
                    }

                    // registrations should be done after select,
                    // once the cancelled keys have been actually removed
                    RegistrationState registration;
                    while((registration = state.statesToBeRegistered.poll()) != null) {
                        int operations = registration.operations;
                        registration.state.getChannel().register(selector, operations, registration.state);
                    }

                    if (select > 0) {
                        idlenessCount = 0;
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();

                            SocketChannel channel = (SocketChannel) key.channel();
                            if (key.isReadable()) {
                                final SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
                                try {
                                    channel.read(buffer);
                                    buffer.flip();
                                    while(buffer.hasRemaining()) {
                                        Frame frame = Frame.readFrom(channel, buffer);

                                        try {
                                            boolean noProblem = state.getConnection().handleReadFrame(frame);
                                            if(noProblem && (!state.getConnection().isRunning() || state.getConnection().hasBrokerInitiatedShutdown())) {
                                                // looks like the frame was Close-Ok or Close
                                                dispatchShutdownToConnection(state);
                                                key.cancel();
                                                break;
                                            }

                                        } catch(Throwable ex) {
                                            // problem during frame processing, tell connection, and
                                            // we can stop for this channel
                                            handleIoError(state, ex);
                                            key.cancel();
                                            break;
                                        }

                                        if(!buffer.hasRemaining()) {
                                            buffer.clear();
                                            channel.read(buffer);
                                            buffer.flip();
                                        }

                                    }
                                    state.setLastActivity(System.currentTimeMillis());
                                } catch (final Exception e) {
                                    LOGGER.warn("Error during reading frames", e);
                                    handleIoError(state, e);
                                    key.cancel();
                                } finally {
                                    buffer.clear();
                                }



                            }
                        }
                    }
                }
            } catch(Exception e) {
                LOGGER.error("Error in read loop", e);
            }
        }
    }

    private class WriteLoop implements Runnable {

        private final SelectorState state;

        public WriteLoop(SelectorState state) {
            this.state = state;
        }

        @Override
        public void run() {
            Selector selector = state.selector;
            // FIXME find a better default?
            ByteBuffer buffer = ByteBuffer.allocate(8192);

            try {
                while(true && !Thread.currentThread().isInterrupted()) {
                    int select;
                    if(state.statesToBeRegistered.isEmpty()) {
                        // we can block, registration will call Selector.wakeup()
                        select = selector.select();
                    } else {
                        // we cannot block, we need to select and clean cancelled keys before registration
                        select = selector.selectNow();
                    }

                    // registrations should be done after select,
                    // once the cancelled keys have been actually removed
                    RegistrationState registration;
                    while((registration = state.statesToBeRegistered.poll()) != null) {
                        int operations = registration.operations;
                        try {
                            registration.state.getChannel().register(selector, operations, registration.state);
                        } catch(Exception e) {
                            // can happen if the channel has been closed since the operation has been enqueued
                            LOGGER.info("Error while registering socket channel for write: {}", e.getMessage());
                        }

                    }

                    if(select > 0) {
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            SocketChannel channel = (SocketChannel) key.channel();
                            SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
                            if (key.isWritable()) {
                                try {
                                    int toBeWritten = state.getWriteQueue().size();
                                    // FIXME property handle header sending request
                                    if(state.isSendHeader()) {
                                        buffer.put("AMQP".getBytes("US-ASCII"));
                                        buffer.put((byte) 0);
                                        buffer.put((byte) AMQP.PROTOCOL.MAJOR);
                                        buffer.put((byte) AMQP.PROTOCOL.MINOR);
                                        buffer.put((byte) AMQP.PROTOCOL.REVISION);
                                        buffer.flip();
                                        while(buffer.hasRemaining() && channel.write(buffer) != -1);
                                        buffer.clear();
                                        state.setSendHeader(false);
                                    }

                                    int written = 0;
                                    Frame frame;
                                    while(written <= toBeWritten && (frame = state.getWriteQueue().poll()) != null) {
                                        frame.writeTo(channel, buffer);
                                        written++;
                                    }
                                    Frame.drain(channel, buffer);
                                } catch(Exception e) {
                                    handleIoError(state, e);
                                } finally {
                                    buffer.clear();
                                    key.cancel();
                                }
                            }
                        }
                    }
                }
            } catch(Exception e) {
                LOGGER.error("Error in write loop", e);
            }
        }

    }

    protected void handleIoError(SocketChannelFrameHandlerState state, Throwable ex) {
        if(needToDispatchIoError(state)) {
            dispatchIoErrorToConnection(state, ex);
        }
    }

    protected boolean needToDispatchIoError(final SocketChannelFrameHandlerState state) {
        return state.getConnection().isOpen();
    }

    protected void dispatchIoErrorToConnection(final SocketChannelFrameHandlerState state, final Throwable ex) {
        // In case of recovery after the shutdown,
        // the new connection shouldn't be initialized in
        // the NIO thread, to avoid a deadlock.
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                state.getConnection().handleIoError(ex);
            }
        };
        if(this.executorService == null) {
            String name = "rabbitmq-connection-shutdown-" + state.getConnection();
            Thread shutdownThread = Environment.newThread(threadFactory, shutdown, name);
            shutdownThread.start();
        } else {
            this.executorService.submit(shutdown);
        }
    }

    protected void dispatchShutdownToConnection(final SocketChannelFrameHandlerState state) {
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                state.getConnection().doFinalShutdown();
            }
        };
        if(this.executorService == null) {
            String name = "rabbitmq-connection-shutdown-" + state.getConnection();
            Thread shutdownThread = Environment.newThread(threadFactory, shutdown, name);
            shutdownThread.start();
        } else {
            this.executorService.submit(shutdown);
        }
    }

    public static class RegistrationState {

        private final SocketChannelFrameHandlerState state;
        private final int operations;

        private RegistrationState(SocketChannelFrameHandlerState state, int operations) {
            this.state = state;
            this.operations = operations;
        }

    }

    public static class SelectorState {

        private final Selector selector;

        private final Queue<RegistrationState> statesToBeRegistered = new LinkedBlockingQueue<RegistrationState>();

        private SelectorState(Selector selector) {
            this.selector = selector;
        }

        public void registerFrameHandlerState(SocketChannelFrameHandlerState state, int operations) {
            statesToBeRegistered.add(new RegistrationState(state, operations));
            selector.wakeup();
        }

    }

}
