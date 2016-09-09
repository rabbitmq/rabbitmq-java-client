package com.rabbitmq.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
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

    private final SocketChannelFrameHandlerFactory.SelectorState selectorState;

    public SocketChannelFrameHandlerState(SocketChannel channel, SocketChannelFrameHandlerFactory.SelectorState selectorState) {
        this.channel = channel;
        this.selectorState = selectorState;
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
            this.selectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
        }
    }

    public void write(Frame frame) throws IOException {
        try {
            boolean offered = this.writeQueue.offer(frame, 10, TimeUnit.SECONDS);
            if(offered) {
                this.selectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
            } else {
                throw new IOException("Frame enqueuing failed");
            }

        } catch (InterruptedException e) {
            LOGGER.warn("Thread interrupted during enqueuing frame in write queue");
        }
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
