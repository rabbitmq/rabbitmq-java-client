package com.rabbitmq.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class SocketChannelFrameHandlerState {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelFrameHandlerState.class);

    private final SocketChannel channel;

    // FIXME find appropriate default for limit in write queue
    private final BlockingQueue<Frame> writeQueue = new LinkedBlockingQueue<Frame>(10000);

    private volatile AMQConnection connection;

    private volatile boolean sendHeader = false;

    /** should be used only in the NIO read thread */
    private long lastActivity;

    private final SocketChannelFrameHandlerFactory.SelectorState writeSelectorState;

    private final SocketChannelFrameHandlerFactory.SelectorState readSelectorState;

    public SocketChannelFrameHandlerState(SocketChannel channel, SocketChannelFrameHandlerFactory.SelectorState readSelectorState,
        SocketChannelFrameHandlerFactory.SelectorState writeSelectorState) {
        this.channel = channel;
        this.readSelectorState = readSelectorState;
        this.writeSelectorState = writeSelectorState;
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
            boolean offered = this.writeQueue.offer(frame, 10, TimeUnit.SECONDS);
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
