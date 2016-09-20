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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

/**
 *
 */
public class WriteLoop extends AbstractNioLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriteLoop.class);

    private final NioLoopsState state;

    public WriteLoop(NioParams nioParams, NioLoopsState loopsState) {
        super(nioParams);
        this.state = loopsState;
    }

    @Override
    public void run() {
        final SelectorHolder selectorState = state.writeSelectorState;
        final Selector selector = selectorState.selector;
        final Set<SocketChannelRegistration> registrations = selectorState.registrations;

        try {
            while (true && !Thread.currentThread().isInterrupted()) {
                int select;
                if (registrations.isEmpty()) {
                    // we can block, registration will call Selector.wakeup()
                    select = selector.select();
                } else {
                    // we cannot block, we need to select and clean cancelled keys before registration
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
                    try {
                        if(registration.state.getChannel().isOpen()) {
                            registration.state.getChannel().register(selector, operations, registration.state);
                        }
                    } catch (Exception e) {
                        // can happen if the channel has been closed since the operation has been enqueued
                        LOGGER.info("Error while registering socket channel for write: {}", e.getMessage());
                    }
                }

                if (select > 0) {
                    Set<SelectionKey> readyKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = readyKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        SocketChannelFrameHandlerState state = (SocketChannelFrameHandlerState) key.attachment();

                        if (key.isWritable()) {
                            boolean cancelKey = true;
                            try {
                                if(!state.getChannel().isOpen()) {
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
            LOGGER.error("Error in write loop", e);
        }
    }
}
