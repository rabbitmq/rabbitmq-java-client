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

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
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

    private final BlockingQueue<WriteRequest> writeQueue;

    private volatile AMQConnection connection;

    /** should be used only in the NIO read thread */
    private long lastActivity;

    private final SelectorHolder writeSelectorState;

    private final SelectorHolder readSelectorState;

    private final int writeEnqueuingTimeoutInMs;

    final boolean ssl;

    final SSLEngine sslEngine;

    /** app data to be crypted before sending */
    final ByteBuffer localAppData;
    /** crypted data to be sent */
    final ByteBuffer localNetData;
    /** app data received and decrypted */
    final ByteBuffer peerAppData;
    /** crypted data received */
    final ByteBuffer peerNetData;

    public SocketChannelFrameHandlerState(SocketChannel channel, SelectorHolder readSelectorState,
        SelectorHolder writeSelectorState, NioParams nioParams, SSLEngine sslEngine) {
        this.channel = channel;
        this.readSelectorState = readSelectorState;
        this.writeSelectorState = writeSelectorState;
        this.writeQueue = new ArrayBlockingQueue<WriteRequest>(nioParams.getWriteQueueCapacity(), true);
        this.writeEnqueuingTimeoutInMs = nioParams.getWriteEnqueuingTimeoutInMs();
        this.sslEngine = sslEngine;
        if(this.sslEngine == null) {
            this.ssl = false;
            this.localAppData = null;
            this.localNetData = null;
            this.peerAppData = null;
            this.peerNetData = null;
        } else {
            this.ssl = true;
            this.localAppData = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
            this.localNetData = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
            this.peerAppData = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
            this.peerNetData = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public Queue<WriteRequest> getWriteQueue() {
        return writeQueue;
    }

    public void sendHeader() throws IOException {
        sendWriteRequest(new HeaderWriteRequest());
    }

    public void write(Frame frame) throws IOException {
        sendWriteRequest(new FrameWriteRequest(frame));
    }

    private void sendWriteRequest(WriteRequest writeRequest) throws IOException {
        try {
            boolean offered = this.writeQueue.offer(writeRequest, writeEnqueuingTimeoutInMs, TimeUnit.MILLISECONDS);
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
