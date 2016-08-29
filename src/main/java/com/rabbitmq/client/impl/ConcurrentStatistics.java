package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class ConcurrentStatistics implements StatisticsCollector {

    private final AtomicLong connectionCount = new AtomicLong(0);
    private final AtomicLong channelCount = new AtomicLong(0);
    private final AtomicLong publishedMessageCount = new AtomicLong(0);
    private final AtomicLong consumedMessageCount = new AtomicLong(0);

    @Override
    public void newConnection(final Connection connection) {
        connectionCount.incrementAndGet();
        connection.addShutdownListener(new ShutdownListener() {
            @Override
            public void shutdownCompleted(ShutdownSignalException cause) {
                closeConnection(connection);
            }
        });
    }

    @Override
    public void closeConnection(Connection connection) {
        connectionCount.decrementAndGet();
    }

    @Override
    public void newChannel(final Channel channel) {
        channelCount.incrementAndGet();
        channel.addShutdownListener(new ShutdownListener() {
            @Override
            public void shutdownCompleted(ShutdownSignalException cause) {
                closeChannel(channel);
            }
        });
    }

    @Override
    public void closeChannel(Channel channel) {
        channelCount.decrementAndGet();
    }

    @Override
    public void command(Connection connection, Channel channel, Command command) {

    }

    @Override
    public void basicPublish(Channel channel) {
        publishedMessageCount.incrementAndGet();
    }

    @Override
    public void consumedMessage(Channel channel) {
        consumedMessageCount.incrementAndGet();
    }

    @Override
    public void clear() {
        connectionCount.set(0);
        channelCount.set(0);
        publishedMessageCount.set(0);
        consumedMessageCount.set(0);
    }

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
        return 0;
    }

    @Override
    public long getRejectedMessageCount() {
        return 0;
    }

}
