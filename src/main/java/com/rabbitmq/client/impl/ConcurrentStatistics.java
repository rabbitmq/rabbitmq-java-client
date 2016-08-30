package com.rabbitmq.client.impl;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class ConcurrentStatistics extends BaseStatistics {

    private final AtomicLong connectionCount = new AtomicLong(0);
    private final AtomicLong channelCount = new AtomicLong(0);
    private final AtomicLong publishedMessageCount = new AtomicLong(0);
    private final AtomicLong consumedMessageCount = new AtomicLong(0);
    private final AtomicLong acknowledgedMessageCount = new AtomicLong(0);
    private final AtomicLong rejectedMessageCount = new AtomicLong(0);

    @Override
    public long getConnectionCount() {
        return connectionCount.get();
    }

    @Override
    public long getChannelCount() {
        return channelCount.get();
    }

    @Override
    public long getPublishedMessageCount() {
        return publishedMessageCount.get();
    }

    @Override
    public long getConsumedMessageCount() {
        return consumedMessageCount.get();
    }

    @Override
    public long getAcknowledgedMessageCount() {
        return acknowledgedMessageCount.get();
    }

    @Override
    public long getRejectedMessageCount() {
        return rejectedMessageCount.get();
    }

    @Override
    protected void incrementConnectionCount() {
        connectionCount.incrementAndGet();
    }

    @Override
    protected void decrementConnectionCount() {
        connectionCount.decrementAndGet();
    }

    @Override
    protected void incrementChannelCount() {
        channelCount.incrementAndGet();
    }

    @Override
    protected void addToChannelCount(long nbChannel) {
        channelCount.addAndGet(nbChannel);
    }

    @Override
    protected void decrementChannelCount() {
        channelCount.decrementAndGet();
    }

    @Override
    protected void incrementPublishedMessageCount() {
        publishedMessageCount.incrementAndGet();
    }

    @Override
    protected void incrementConsumedMessageCount() {
        consumedMessageCount.incrementAndGet();
    }

    @Override
    protected void incrementAcknowledgedMessageCount() {
        acknowledgedMessageCount.incrementAndGet();
    }

    @Override
    protected void incrementRejectedMessageCount() {
        rejectedMessageCount.incrementAndGet();
    }

    @Override
    protected void resetConnectionCount() {
        connectionCount.set(0);
    }

    @Override
    protected void resetChannelCount() {
        channelCount.set(0);
    }

    @Override
    protected void resetPublishedMessageCount() {
        publishedMessageCount.set(0);
    }

    @Override
    protected void resetConsumedMessageCount() {
        consumedMessageCount.set(0);
    }

    @Override
    protected void resetAcknowledgedMessageCount() {
        acknowledgedMessageCount.set(0);
    }

    @Override
    protected void resetRejectedMessageCount() {
        rejectedMessageCount.set(0);
    }
}
