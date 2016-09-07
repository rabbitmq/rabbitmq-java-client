package com.rabbitmq.client.impl;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 */
public class SocketChannelFrameHandlerState {

    private final SocketChannel channel;

    private volatile SelectionKey readSelectionKey;

    private volatile SelectionKey writeSelectionKey;

    private final Queue<Frame> writeQueue = new LinkedBlockingQueue<Frame>();

    private final BlockingQueue<Frame> readQueue = new LinkedBlockingQueue<Frame>();

    private volatile boolean sendHeader = false;

    private final SocketChannelFrameHandlerFactory.SelectorState selectorState;

    public SocketChannelFrameHandlerState(SocketChannel channel, SocketChannelFrameHandlerFactory.SelectorState selectorState) {
        this.channel = channel;
        this.selectorState = selectorState;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void setReadSelectionKey(SelectionKey readSelectionKey) {
        this.readSelectionKey = readSelectionKey;
    }

    public void setWriteSelectionKey(SelectionKey writeSelectionKey) {
        this.writeSelectionKey = writeSelectionKey;
    }

    public void addReadFrame(Frame frame) {
        this.readQueue.add(frame);
    }

    public BlockingQueue<Frame> getReadQueue() {
        return readQueue;
    }

    public Queue<Frame> getWriteQueue() {
        return writeQueue;
    }

    public SelectionKey getReadSelectionKey() {
        return readSelectionKey;
    }

    public SelectionKey getWriteSelectionKey() {
        return writeSelectionKey;
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

    public void write(Frame frame) {
        this.writeQueue.add(frame);
        this.selectorState.registerFrameHandlerState(this, SelectionKey.OP_WRITE);
    }
}
