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

import com.rabbitmq.client.impl.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 *
 */
public class ReadLoop extends AbstractNioLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadLoop.class);

    private final NioLoopsState state;

    public ReadLoop(NioParams nioParams, NioLoopsState readSelectorState) {
        super(nioParams);
        this.state = readSelectorState;
    }

    @Override
    public void run() {
        final SelectorHolder selectorState = state.readSelectorState;
        final Selector selector = selectorState.selector;
        Set<SocketChannelRegistration> registrations = selectorState.registrations;
        // FIXME find a better default?
        ByteBuffer buffer = ByteBuffer.allocate(nioParams.getReadByteBufferSize());
        try {
            int idlenessCount = 0;
            while (true && !Thread.currentThread().isInterrupted()) {

                for (SelectionKey selectionKey : selector.keys()) {
                    SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) selectionKey.attachment();
                    if (state.getConnection().getHeartbeat() > 0) {
                        long now = System.currentTimeMillis();
                        if ((now - state.getLastActivity()) > state.getConnection().getHeartbeat() * 1000 * 2) {
                            try {
                                state.getConnection().handleHeartbeatFailure();
                            } catch (Exception e) {
                                LOGGER.warn("Error after heartbeat failure of connection {}", state.getConnection());
                            } finally {
                                selectionKey.cancel();
                            }
                        }
                    }
                }

                int select;
                if (registrations.isEmpty()) {
                    // we can block, registration will call Selector.wakeup()
                    select = selector.select(1000);
                    idlenessCount++;
                    if (idlenessCount == 10 && selector.keys().size() == 0) {
                        //if(false) {
                        // we haven't been doing anything for a while, shutdown state
                        boolean clean = state.cleanUp();
                        if (clean) {
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
                SocketChannelRegistration registration;
                Iterator<SocketChannelRegistration> registrationIterator = registrations.iterator();
                while (registrationIterator.hasNext()) {
                    registration = registrationIterator.next();
                    registrationIterator.remove();
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
                                if (state.ssl) {
                                    ByteBuffer peerAppData = state.peerAppData;
                                    ByteBuffer peerNetData = state.peerNetData;
                                    SSLEngine engine = state.sslEngine;

                                    peerNetData.clear();
                                    peerAppData.clear();

                                    peerNetData.flip();
                                    peerAppData.flip();

                                    // FIXME reuse input stream
                                    SslEngineByteBufferInputStream sslEngineByteBufferInputStream = new SslEngineByteBufferInputStream(
                                        engine, peerAppData, peerNetData, channel
                                    );

                                    DataInputStream inputStream = new DataInputStream(sslEngineByteBufferInputStream);

                                    while (true) {
                                        Frame frame = Frame.readFrom(inputStream);

                                        try {
                                            boolean noProblem = state.getConnection().handleReadFrame(frame);
                                            if (noProblem && (!state.getConnection().isRunning() || state.getConnection().hasBrokerInitiatedShutdown())) {
                                                // looks like the frame was Close-Ok or Close
                                                dispatchShutdownToConnection(state);
                                                key.cancel();
                                                break;
                                            }
                                        } catch (Throwable ex) {
                                            // problem during frame processing, tell connection, and
                                            // we can stop for this channel
                                            handleIoError(state, ex);
                                            key.cancel();
                                            break;
                                        }

                                        if (!peerAppData.hasRemaining() && !peerNetData.hasRemaining()) {
                                            // need to try to read something
                                            peerNetData.clear();
                                            int bytesRead = channel.read(peerNetData);
                                            if (bytesRead <= 0) {
                                                break;
                                            } else {
                                                peerNetData.flip();
                                            }
                                        }
                                    }
                                } else {
                                    channel.read(buffer);
                                    buffer.flip();
                                    while (buffer.hasRemaining()) {
                                        Frame frame = Frame.readFrom(channel, buffer);

                                        try {
                                            boolean noProblem = state.getConnection().handleReadFrame(frame);
                                            if (noProblem && (!state.getConnection().isRunning() || state.getConnection().hasBrokerInitiatedShutdown())) {
                                                // looks like the frame was Close-Ok or Close
                                                dispatchShutdownToConnection(state);
                                                key.cancel();
                                                break;
                                            }
                                        } catch (Throwable ex) {
                                            // problem during frame processing, tell connection, and
                                            // we can stop for this channel
                                            handleIoError(state, ex);
                                            key.cancel();
                                            break;
                                        }

                                        if (!buffer.hasRemaining()) {
                                            buffer.clear();
                                            channel.read(buffer);
                                            buffer.flip();
                                        }
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
        } catch (Exception e) {
            LOGGER.error("Error in read loop", e);
        }
    }
}
