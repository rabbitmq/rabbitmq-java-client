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

/**
 *
 */
public class SocketChannelFrameHandlerFactory extends FrameHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerFactory.class);

    private final SelectorState readSelectorState;
    private final SelectorState writeSelectorState;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator,
        boolean ssl) throws IOException {
        super(connectionTimeout, factory, configurator, ssl);
        this.readSelectorState = new SelectorState(Selector.open());
        this.writeSelectorState = new SelectorState(Selector.open());
        this.executorService.submit(new ReadLoop(readSelectorState));
        this.executorService.submit(new WriteLoop(writeSelectorState));
    }

    public SocketChannelFrameHandlerFactory(int connectionTimeout, SocketFactory factory, SocketConfigurator configurator, boolean ssl,
        ExecutorService shutdownExecutor) throws IOException {
        super(connectionTimeout, factory, configurator, ssl, shutdownExecutor);
        this.readSelectorState = new SelectorState(Selector.open());
        this.writeSelectorState = new SelectorState(Selector.open());
        this.executorService.submit(new ReadLoop(readSelectorState));
        this.executorService.submit(new WriteLoop(writeSelectorState));
    }

    @Override
    public FrameHandler create(Address addr) throws IOException {
        SocketAddress address = new InetSocketAddress("localhost", 5672);
        SocketChannel channel = SocketChannel.open();
        configurator.configure(channel.socket());
        channel.configureBlocking(false);

        channel.connect(address);

        SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(channel, writeSelectorState);

        CountDownLatch latch = new CountDownLatch(2);
        readSelectorState.registerFrameHandlerState(state, latch, SelectionKey.OP_READ);
        writeSelectorState.registerFrameHandlerState(state, latch, SelectionKey.OP_CONNECT);

        // FIXME make timeout a parameter
        try {
            boolean selectorRegistered = latch.await(5, TimeUnit.SECONDS);
            if(!selectorRegistered) {
                throw new IOException("Could not register socket channel in IO loops");
            }
        } catch (InterruptedException e) {
            throw new IOException("Could not register socket channel in IO loops", e);
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

        public void registerFrameHandlerState(SocketChannelFrameHandlerState state, CountDownLatch latch, int operations) {
            statesToBeRegistered.add(new RegistrationState(state, latch, operations));
            selector.wakeup();
        }

        public void registerFrameHandlerState(SocketChannelFrameHandlerState state, int operations) {
            registerFrameHandlerState(state, null, operations);
        }

    }

    private static class ReadLoop implements Runnable {

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
                    RegistrationState registration;
                    while((registration = state.statesToBeRegistered.poll()) != null) {
                        int operations = registration.operations;
                        SelectionKey readKey = registration.state.getChannel().register(selector, operations);
                        readKey.attach(registration.state);
                        registration.state.setReadSelectionKey(readKey);
                        registration.done();
                    }

                    int select = selector.select();
                    if (select > 0) {
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();

                            SocketChannel channel = (SocketChannel) key.channel();
                            if (key.isReadable()) {
                                SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();
                                channel.read(buffer);
                                buffer.flip();
                                // FIXME handle partial frame
                                while(buffer.hasRemaining()) {
                                    Frame frame = Frame.readFrom(channel, buffer);
                                    state.addReadFrame(frame);
                                }
                                buffer.clear();
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
                while(true) {
                    RegistrationState registration;
                    while((registration = state.statesToBeRegistered.poll()) != null) {
                        int operations = registration.operations;
                        SelectionKey writeKey = registration.state.getChannel().register(selector, operations);
                        writeKey.attach(registration.state);
                        registration.state.setWriteSelectionKey(writeKey);
                        registration.done();
                    }

                    int select = selector.select();
                    if(select > 0) {
                        Set<SelectionKey> readyKeys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = readyKeys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            SocketChannel channel = (SocketChannel) key.channel();
                            if (key.isConnectable()) {
                                if (!channel.isConnected()) {
                                    channel.finishConnect();
                                }
                            } else if (key.isWritable()) {
                                SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();

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

                                Frame frame;
                                while((frame = state.getWriteQueue().poll()) != null) {
                                    frame.writeTo(channel, buffer);
                                    buffer.clear();
                                }
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
        private final CountDownLatch latch;
        private final int operations;

        private RegistrationState(SocketChannelFrameHandlerState state, CountDownLatch latch, int operations) {
            this.state = state;
            this.latch = latch;
            this.operations = operations;
        }

        public void done() {
            if(latch != null) {
                latch.countDown();
            }
        }
    }
}
