package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class ConcurrentStatistics implements StatisticsCollector {

    // TODO keep track of Connections and wipe-out the state of closed connections in a separate thread
    // TODO protect each call in a try/catch block (for core features not to fail because of stats)
    // TODO consider a single-threaded implementation?

    private final AtomicLong connectionCount = new AtomicLong(0);
    private final AtomicLong channelCount = new AtomicLong(0);
    private final AtomicLong publishedMessageCount = new AtomicLong(0);
    private final AtomicLong consumedMessageCount = new AtomicLong(0);
    private final AtomicLong acknowledgedMessageCount = new AtomicLong(0);
    private final AtomicLong rejectedMessageCount = new AtomicLong(0);

    private final ConcurrentMap<String, ConnectionState> connectionState = new ConcurrentHashMap<String, ConnectionState>();

    @Override
    public void newConnection(final Connection connection) {
        if(connection.getId() == null) {
            connection.setId(UUID.randomUUID().toString());
        }
        connectionCount.incrementAndGet();
        connectionState.put(connection.getId(), new ConnectionState());
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
        connectionState.remove(connection.getId());
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
        connectionState(channel.getConnection()).channelState.put(channel.getChannelNumber(), new ChannelState());
    }

    @Override
    public void closeChannel(Channel channel) {
        channelCount.decrementAndGet();
        connectionState(channel.getConnection()).channelState.remove(channel.getChannelNumber(), new ChannelState());
    }

    @Override
    public void command(Connection connection, Channel channel, Command command) {

    }

    @Override
    public void basicPublish(Channel channel) {
        publishedMessageCount.incrementAndGet();
    }

    @Override
    public void basicConsume(Channel channel, String consumerTag, boolean autoAck) {
        if(!autoAck) {
            ChannelState channelState = channelState(channel);
            channelState.lock.lock();
            try {
                channelState(channel).consumersWithManualAck.add(consumerTag);
            } finally {
                channelState.lock.unlock();
            }

        }
    }

    // TODO implement cancelConsume

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, boolean autoAck) {
        consumedMessageCount.incrementAndGet();
        if(!autoAck) {
            ChannelState channelState = channelState(channel);
            channelState.lock.lock();
            try {
                channelState(channel).unackedMessageDeliveryTags.add(deliveryTag);
            } finally {
                channelState.lock.unlock();
            }
        }
    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, String consumerTag) {
        consumedMessageCount.incrementAndGet();
        ChannelState channelState = channelState(channel);
        channelState.lock.lock();
        try {
            if(channelState.consumersWithManualAck.contains(consumerTag)) {
                channelState.unackedMessageDeliveryTags.add(deliveryTag);
            }
        } finally {
            channelState.lock.unlock();
        }

    }

    @Override
    public void basicAck(Channel channel, long deliveryTag, boolean multiple) {
        updateChannelStateAfterAckReject(channel, deliveryTag, multiple, acknowledgedMessageCount);
    }

    @Override
    public void basicNack(Channel channel, long deliveryTag) {
        updateChannelStateAfterAckReject(channel, deliveryTag, true, rejectedMessageCount);
    }

    @Override
    public void basicReject(Channel channel, long deliveryTag) {
        updateChannelStateAfterAckReject(channel, deliveryTag, false, rejectedMessageCount);
    }

    private void updateChannelStateAfterAckReject(Channel channel, long deliveryTag, boolean multiple, AtomicLong counter) {
        ChannelState channelState = channelState(channel);
        channelState.lock.lock();
        try {
            if(multiple) {
                Iterator<Long> iterator = channelState.unackedMessageDeliveryTags.iterator();
                while(iterator.hasNext()) {
                    long messageDeliveryTag = iterator.next();
                    if(messageDeliveryTag <= deliveryTag) {
                        iterator.remove();
                        counter.incrementAndGet();
                    }
                }
            } else {
                channelState.unackedMessageDeliveryTags.remove(deliveryTag);
                counter.incrementAndGet();
            }
        } finally {
            channelState.lock.unlock();
        }
    }

    @Override
    public void clear() {
        connectionCount.set(0);
        channelCount.set(0);
        publishedMessageCount.set(0);
        consumedMessageCount.set(0);
        acknowledgedMessageCount.set(0);
        rejectedMessageCount.set(0);

        connectionState.clear();
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
        return acknowledgedMessageCount.get();
    }

    @Override
    public long getRejectedMessageCount() {
        return rejectedMessageCount.get();
    }

    private ConnectionState connectionState(Connection connection) {
        return connectionState.get(connection.getId());
    }

    private ChannelState channelState(Channel channel) {
        return connectionState(channel.getConnection()).channelState.get(channel.getChannelNumber());
    }

    private static class ConnectionState {

        final ConcurrentMap<Integer, ChannelState> channelState = new ConcurrentHashMap<Integer, ChannelState>();

    }

    private static class ChannelState {

        final Lock lock = new ReentrantLock();

        final Set<Long> unackedMessageDeliveryTags = new HashSet<Long>();
        final Set<String> consumersWithManualAck = new HashSet<String>();

    }


}
