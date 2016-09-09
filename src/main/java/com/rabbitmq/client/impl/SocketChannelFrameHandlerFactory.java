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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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
        SocketAddress address = new InetSocketAddress(addr.getHost(), addr.getPort());
        SocketChannel channel = SocketChannel.open();
        configurator.configure(channel.socket());

        // FIXME handle connection failure
        channel.connect(address);

        channel.configureBlocking(false);

        SocketChannelFrameHandlerState state = new SocketChannelFrameHandlerState(channel, writeSelectorState);

        readSelectorState.registerFrameHandlerState(state, SelectionKey.OP_READ);

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
                    int select;
                    if(state.statesToBeRegistered.isEmpty()) {
                        // we can block, registration will call Selector.wakeup()

                        // FIXME check the number of keys and stop the read and write loops
                        // if there's no read key anymore

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
                                        // we can stop
                                        if(!handled) {
                                            break;
                                        }

                                        if(!buffer.hasRemaining()) {
                                            buffer.clear();
                                            channel.read(buffer);
                                            buffer.flip();
                                        }

                                    }

                                } catch (Exception e) {
                                    LOGGER.warn("Error during reading frames: "+e.getMessage());
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
                while(true) {
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
