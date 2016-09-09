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
import com.rabbitmq.client.SocketConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends FrameHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerFactory.class);

    private final SelectorState readSelectorState;
    private final SelectorState writeSelectorState;

    private ExecutorService executorService;

    private Future<?> readLoop;
    private Future<?> writeLoop;

    private Lock loopsLock = new ReentrantLock();

    private final ThreadFactory threadFactory = new ThreadFactory() {

        AtomicLong counter = new AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "rabbitmq-nio-"+counter.getAndIncrement());
        }
    };

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator,
        boolean ssl) throws IOException {
        super(connectionTimeout, factory, configurator, ssl);
        this.readSelectorState = new SelectorState(Selector.open());
        this.writeSelectorState = new SelectorState(Selector.open());
    }

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl,
        ExecutorService shutdownExecutor) throws IOException {
        super(connectionTimeout, factory, configurator, ssl, shutdownExecutor);
        this.readSelectorState = new SelectorState(Selector.open());
        this.writeSelectorState = new SelectorState(Selector.open());
    }

    @Override
    public FrameHandler create(Address addr) throws IOException {
        SocketAddress address = new InetSocketAddress(addr.getHost(), addr.getPort());
        SocketChannel channel = SocketChannel.open();
        configurator.configure(channel.socket());

        // FIXME handle connection failure
        channel.connect(address);

        channel.configureBlocking(false);

        SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(channel, writeSelectorState);

        loopsLock.lock();
        try {
            readSelectorState.registerFrameHandlerState(state, SelectionKey.OP_READ);

            if(this.executorService == null) {

                this.executorService = Executors.newFixedThreadPool(2, threadFactory);
            }
            if(readLoop == null) {
                readLoop = this.executorService.submit(new ReadLoop(readSelectorState));
            }
            if(writeLoop == null) {
                writeLoop = this.executorService.submit(new WriteLoop(writeSelectorState));
            }
        } finally {
            loopsLock.unlock();
        }



        SocketChannelFrameHandler frameHandler = new SocketChannelFrameHandler(state);
        return frameHandler;
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

    private boolean cleanLoopsOrKeepRunning() {
        loopsLock.lock();
        try {
            if(readSelectorState.statesToBeRegistered.isEmpty()) {
                boolean cancelled = writeLoop.cancel(true);
                if(!cancelled) {
                    LOGGER.warn("Could not stop write loop");
                }
                this.writeLoop = null;
                this.readLoop = null;
                this.executorService.shutdownNow();
                return false;
            } else {
                // looks like someone is trying to connect, keep running
                return true;
            }
        } finally {
            loopsLock.unlock();
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
                while(true) {

                    // if there's no read key anymore
                    boolean someoneIsStillReading = false;
                    for (SelectionKey selectionKey : selector.keys()) {
                        SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) selectionKey.attachment();
                        // FIXME connection should always be here
                        if(state.getConnection() != null && state.getConnection().getHeartbeat() > 0) {
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

                        if(!selectionKey.channel().isOpen()) {
                            selectionKey.cancel();
                        } else {
                            someoneIsStillReading = true;
                        }
                    }

                    if(!someoneIsStillReading && state.statesToBeRegistered.isEmpty()) {
                        boolean keepRunning = cleanLoopsOrKeepRunning();
                        if(!keepRunning) {
                            return;
                        }
                    }

                    int select;
                    if(state.statesToBeRegistered.isEmpty()) {
                        // we can block, registration will call Selector.wakeup()
                        select = selector.select(1000);
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
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();

                            SocketChannel channel = (SocketChannel) key.channel();
                            if (key.isReadable()) {
                                SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
                                try {
                                    channel.read(buffer);
                                    buffer.flip();
                                    while(buffer.hasRemaining()) {
                                        Frame frame = Frame.readFrom(channel, buffer);

                                        // FIXME the connection may not be there yet (to be checked)
                                        boolean handled = state.getConnection().handleReadFrame(frame);

                                        // problem during frame processing, the connection triggered shutdown
                                        // we can stop for this channel
                                        if(!handled) {
                                            break;
                                        }

                                        if(!buffer.hasRemaining()) {
                                            buffer.clear();
                                            channel.read(buffer);
                                            buffer.flip();
                                        }

                                    }
                                    state.setLastActivity(System.currentTimeMillis());
                                } catch (Exception e) {
                                    LOGGER.warn("Error during reading frames", e);
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

    private static class WriteLoop implements Runnable {

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
                        registration.state.getChannel().register(selector, operations, registration.state);
                    }

                    if(select > 0) {
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            SocketChannel channel = (SocketChannel) key.channel();
                            if (key.isWritable()) {
                                SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
                                int toBeWritten = state.getWriteQueue().size();
                                // FIXME property handle header sending request
                                if(state.isSendHeader()) {
                                    buffer.put("AMQP".getBytes("US-ASCII"));
                                    buffer.put((byte) 0);
                                    buffer.put((byte) AMQP.PROTOCOL.MAJOR);
                                    buffer.put((byte) AMQP.PROTOCOL.MINOR);
                                    buffer.put((byte) AMQP.PROTOCOL.REVISION);
                                    buffer.flip();
                                    while(buffer.hasRemaining() && channel.write(buffer) != 0);
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
                            }
                            key.cancel();
                        }
                    }

                }
            } catch(Exception e) {
                LOGGER.error("Error in write loop", e);
            }
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
}
