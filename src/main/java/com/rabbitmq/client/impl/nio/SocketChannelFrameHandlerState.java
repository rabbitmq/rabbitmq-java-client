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

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class SocketChannelFrameHandlerState {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerState.class);

    private final SocketChannel channel;

    private final BlockingQueue<Frame> writeQueue;

    private volatile AMQConnection connection;

    private volatile boolean sendHeader = false;

    /** should be used only in the NIO read thread */
    private long lastActivity;

    private final SelectorHolder writeSelectorState;

    private final SelectorHolder readSelectorState;

    private final int writeEnqueuingTimeoutInMs;

    public SocketChannelFrameHandlerState(SocketChannel channel, SelectorHolder readSelectorState,
        SelectorHolder writeSelectorState, NioParams nioParams) {
        this.channel = channel;
        this.readSelectorState = readSelectorState;
        this.writeSelectorState = writeSelectorState;
        this.writeQueue = new ArrayBlockingQueue<Frame>(nioParams.getWriteQueueCapacity(), true);
        this.writeEnqueuingTimeoutInMs = nioParams.getWriteEnqueuingTimeoutInMs();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public Queue<Frame> getWriteQueue() {
        return writeQueue;
    }

    public boolean isSendHeader() {
        return sendHeader;
    }

    public void setSendHeader(boolean sendHeader) {
        this.sendHeader = sendHeader;
        if(sendHeader) {
            this.writeSelectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
        }
    }

    public void write(Frame frame) throws IOException {
        try {
            boolean offered = this.writeQueue.offer(frame, writeEnqueuingTimeoutInMs, TimeUnit.MILLISECONDS);
            if(offered) {
                this.writeSelectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
            } else {
                throw new IOException("Frame enqueuing failed");
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Thread interrupted during enqueuing frame in write queue");
        }
    }

    public void startReading() {
        this.readSelectorState.registerFrameHandlerState(this, SelectionKey.OP_READ);
    }

    public AMQConnection getConnection() {
        return connection;
    }

    public void setConnection(AMQConnection connection) {
        this.connection = connection;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    public long getLastActivity() {
        return lastActivity;
    }
}
