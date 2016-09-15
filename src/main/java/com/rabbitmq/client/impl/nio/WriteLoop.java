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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 *
 */
public class WriteLoop extends AbstractNioLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriteLoop.class);

    private final SelectorHolder state;

    public WriteLoop(NioParams nioParams, SelectorHolder state) {
        super(nioParams);
        this.state = state;
    }

    @Override
    public void run() {
        Selector selector = state.selector;
        // FIXME find a better default?
        ByteBuffer buffer = ByteBuffer.allocate(nioParams.getWriteByteBufferSize());

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
                SocketChannelRegistration registration;
                Iterator<SocketChannelRegistration> registrationIterator = state.statesToBeRegistered.iterator();
                while(registrationIterator.hasNext()) {
                    registration = registrationIterator.next();
                    registrationIterator.remove();
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
                            boolean cancelKey = true;
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
                                if(!state.getWriteQueue().isEmpty()) {
                                    cancelKey = true;
                                }
                            } catch(Exception e) {
                                handleIoError(state, e);
                            } finally {
                                buffer.clear();
                                if(cancelKey) {
                                    key.cancel();
                                }
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
