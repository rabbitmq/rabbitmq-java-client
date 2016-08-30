package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentStatistics.class);

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
        connectionState.put(connection.getId(), new ConnectionState(connection));
        connection.addShutdownListener(new ShutdownListener() {
            @Override
            public void shutdownCompleted(ShutdownSignalException cause) {
                closeConnection(connection);
            }
        });
    }

    @Override
    public void closeConnection(Connection connection) {
        ConnectionState removed = connectionState.remove(connection.getId());
        if(removed != null) {
            connectionCount.decrementAndGet();
        }
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
        connectionState(channel.getConnection()).channelState.put(channel.getChannelNumber(), new ChannelState(channel));
    }

    @Override
    public void closeChannel(Channel channel) {
        ChannelState removed = connectionState(channel.getConnection()).channelState.remove(channel.getChannelNumber());
        if(removed != null) {
            channelCount.decrementAndGet();
        }
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

    @Override
    public void basicCancel(Channel channel, String consumerTag) {
        ChannelState channelState = channelState(channel);
        channelState.lock.lock();
        try {
            channelState(channel).consumersWithManualAck.remove(consumerTag);
        } finally {
            channelState.lock.unlock();
        }
    }

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

    public void cleanStaleState() {
        try {
            Iterator<Map.Entry<String, ConnectionState>> connectionStateIterator = connectionState.entrySet().iterator();
            while(connectionStateIterator.hasNext()) {
                Map.Entry<String, ConnectionState> connectionEntry = connectionStateIterator.next();
                Connection connection = connectionEntry.getValue().connection;
                if(connection.isOpen()) {
                    Iterator<Map.Entry<Integer, ChannelState>> channelStateIterator = connectionEntry.getValue().channelState.entrySet().iterator();
                    while(channelStateIterator.hasNext()) {
                        Map.Entry<Integer, ChannelState> channelStateEntry = channelStateIterator.next();
                        Channel channel = channelStateEntry.getValue().channel;
                        if(!channel.isOpen()) {
                            channelStateIterator.remove();
                            channelCount.decrementAndGet();
                            LOGGER.info("Ripped off state of channel {} of connection {}. This is abnormal, please report.",
                                channel.getChannelNumber(), connection.getId());
                        }
                    }
                } else {
                    connectionStateIterator.remove();
                    connectionCount.decrementAndGet();
                    channelCount.addAndGet(-connectionEntry.getValue().channelState.size());
                    LOGGER.info("Ripped off state of connection {}. This is abnormal, please report.",
                        connection.getId());
                }
            }
        } catch(Exception e) {
            LOGGER.info("Error during periodic clean of statistics: "+e.getMessage());
        }
    }

    private static class ConnectionState {

        final ConcurrentMap<Integer, ChannelState> channelState = new ConcurrentHashMap<Integer, ChannelState>();
        final Connection connection;

        private ConnectionState(Connection connection) {
            this.connection = connection;
        }
    }

    private static class ChannelState {

        final Lock lock = new ReentrantLock();

        final Set<Long> unackedMessageDeliveryTags = new HashSet<Long>();
        final Set<String> consumersWithManualAck = new HashSet<String>();

        final Channel channel;

        private ChannelState(Channel channel) {
            this.channel = channel;
        }

    }

}
