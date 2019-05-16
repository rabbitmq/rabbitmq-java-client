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

import com.rabbitmq.client.impl.Environment;
import com.rabbitmq.client.impl.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Logic of the NIO loop.
 */
public class NioLoop implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioLoop.class);

    private final NioLoopContext context;

    private final NioParams nioParams;

    private final ExecutorService connectionShutdownExecutor;

    public NioLoop(NioParams nioParams, NioLoopContext loopContext) {
        this.nioParams = nioParams;
        this.context = loopContext;
        this.connectionShutdownExecutor = nioParams.getConnectionShutdownExecutor();
    }

    @Override
    public void run() {
        final SelectorHolder selectorState = context.readSelectorState;
        final Selector selector = selectorState.selector;
        final Set<SocketChannelRegistration> registrations = selectorState.registrations;

        final ByteBuffer buffer = context.readBuffer;

        final SelectorHolder writeSelectorState = context.writeSelectorState;
        final Selector writeSelector = writeSelectorState.selector;
        final Set<SocketChannelRegistration> writeRegistrations = writeSelectorState.registrations;

        // whether there have been write registrations in the previous loop
        // registrations are done after Selector.select(), to work on clean keys
        // thus, any write operation is performed in the next loop
        // we don't want to wait in the read Selector.select() if there are
        // pending writes
        boolean writeRegistered = false;

        try {
            while (!Thread.currentThread().isInterrupted()) {

                for (SelectionKey selectionKey : selector.keys()) {
                    SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) selectionKey.attachment();
                    if (state.getConnection() != null && state.getConnection().getHeartbeat() > 0) {
                        long now = System.currentTimeMillis();
                        if ((now - state.getLastActivity()) > state.getConnection().getHeartbeat() * 1000 * 2) {
                            try {
                                handleHeartbeatFailure(state);
                            } catch (Exception e) {
                                LOGGER.warn("Error after heartbeat failure of connection {}", state.getConnection());
                            } finally {
                                selectionKey.cancel();
                            }
                        }
                    }
                }

                int select;
                if (!writeRegistered && registrations.isEmpty() && writeRegistrations.isEmpty()) {
                    // we can block, registrations will call Selector.wakeup()
                    select = selector.select(1000);
                    if (selector.keys().size() == 0) {
                        // we haven't been doing anything for a while, shutdown state
                        boolean clean = context.cleanUp();
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

                writeRegistered = false;

                // registrations should be done after select,
                // once the cancelled keys have been actually removed
                SocketChannelRegistration registration;
                Iterator<SocketChannelRegistration> registrationIterator = registrations.iterator();
                while (registrationIterator.hasNext()) {
                    registration = registrationIterator.next();
                    registrationIterator.remove();
                    int operations = registration.operations;
                    try {
                        if (registration.state.getChannel().isOpen()) {
                            registration.state.getChannel().register(selector, operations, registration.state);
                        }
                    } catch (Exception e) {
                        // can happen if the channel has been closed since the operation has been enqueued
                        LOGGER.info("Error while registering socket channel for read: {}", e.getMessage());
                    }
                }

                if (select > 0) {
                    Set<SelectionKey> readyKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = readyKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isReadable()) {
                            final SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();

                            try {
                                if (!state.getChannel().isOpen()) {
                                    key.cancel();
                                    continue;
                                }
                                if(state.getConnection() == null) {
                                    // we're in AMQConnection#start, between the header sending and the FrameHandler#initialize
                                    // let's wait a bit more
                                    continue;
                                }

                                state.prepareForReadSequence();

                                while (state.continueReading()) {
                                    final Frame frame = state.frameBuilder.readFrame();

                                    if (frame != null) {
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

                // write loop

                select = writeSelector.selectNow();

                // registrations should be done after select,
                // once the cancelled keys have been actually removed
                SocketChannelRegistration writeRegistration;
                Iterator<SocketChannelRegistration> writeRegistrationIterator = writeRegistrations.iterator();
                while (writeRegistrationIterator.hasNext()) {
                    writeRegistration = writeRegistrationIterator.next();
                    writeRegistrationIterator.remove();
                    int operations = writeRegistration.operations;
                    try {
                        if (writeRegistration.state.getChannel().isOpen()) {
                            writeRegistration.state.getChannel().register(writeSelector, operations, writeRegistration.state);
                            writeRegistered = true;
                        }
                    } catch (Exception e) {
                        // can happen if the channel has been closed since the operation has been enqueued
                        LOGGER.info("Error while registering socket channel for write: {}", e.getMessage());
                    }
                }

                if (select > 0) {
                    Set<SelectionKey> readyKeys = writeSelector.selectedKeys();
                    Iterator<SelectionKey> iterator = readyKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isWritable()) {
                            boolean cancelKey = true;
                            try {
                                if (!state.getChannel().isOpen()) {
                                    key.cancel();
                                    continue;
                                }

                                state.prepareForWriteSequence();

                                int toBeWritten = state.getWriteQueue().size();
                                int written = 0;

                                DataOutputStream outputStream = state.outputStream;

                                WriteRequest request;
                                while (written <= toBeWritten && (request = state.getWriteQueue().poll()) != null) {
                                    request.handle(outputStream);
                                    written++;
                                }
                                outputStream.flush();
                                if (!state.getWriteQueue().isEmpty()) {
                                    cancelKey = true;
                                }
                            } catch (Exception e) {
                                handleIoError(state, e);
                            } finally {
                                state.endWriteSequence();
                                if (cancelKey) {
                                    key.cancel();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in NIO loop", e);
        }
    }

    protected void handleIoError(SocketChannelFrameHandlerState state, Throwable ex) {
        if (needToDispatchIoError(state)) {
            dispatchIoErrorToConnection(state, ex);
        } else {
            try {
                state.close();
            } catch (IOException e) {

            }
        }
    }

    protected void handleHeartbeatFailure(SocketChannelFrameHandlerState state) {
        if (needToDispatchIoError(state)) {
            dispatchShutdownToConnection(
                () -> state.getConnection().handleHeartbeatFailure(),
                state.getConnection().toString()
            );
        } else {
            try {
                state.close();
            } catch (IOException e) {

            }
        }
    }

    protected boolean needToDispatchIoError(final SocketChannelFrameHandlerState state) {
        return state.getConnection().isOpen();
    }

    protected void dispatchIoErrorToConnection(final SocketChannelFrameHandlerState state, final Throwable ex) {
        dispatchShutdownToConnection(
            () -> state.getConnection().handleIoError(ex),
            state.getConnection().toString()
        );
    }

    protected void dispatchShutdownToConnection(final SocketChannelFrameHandlerState state) {
        dispatchShutdownToConnection(
            () -> state.getConnection().doFinalShutdown(),
            state.getConnection().toString()
        );
    }

    protected void dispatchShutdownToConnection(Runnable connectionShutdownRunnable, String connectionName) {
        // In case of recovery after the shutdown,
        // the new connection shouldn't be initialized in
        // the NIO thread, to avoid a deadlock.
        if (this.connectionShutdownExecutor != null) {
            connectionShutdownExecutor.execute(connectionShutdownRunnable);
        } else if (executorService() != null) {
            executorService().execute(connectionShutdownRunnable);
        } else {
            String name = "rabbitmq-connection-shutdown-" + connectionName;
            Thread shutdownThread = Environment.newThread(threadFactory(), connectionShutdownRunnable, name);
            shutdownThread.start();
        }
    }

    private ExecutorService executorService() {
        return nioParams.getNioExecutor();
    }

    private ThreadFactory threadFactory() {
        return nioParams.getThreadFactory();
    }
}
