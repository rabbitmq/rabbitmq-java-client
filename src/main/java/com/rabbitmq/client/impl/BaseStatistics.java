package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public abstract class BaseStatistics implements StatisticsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseStatistics.class);

    // TODO protect each call in a try/catch block (for core features not to fail because of stats)

    private final ConcurrentMap<String, ConnectionState> connectionState = new ConcurrentHashMap<String, ConnectionState>();

    protected abstract void incrementConnectionCount();

    protected abstract void decrementConnectionCount();

    protected abstract void incrementChannelCount();

    protected abstract void addToChannelCount(long nbChannel);

    protected abstract void decrementChannelCount();

    protected abstract void incrementPublishedMessageCount();

    protected abstract void incrementConsumedMessageCount();

    protected abstract void incrementAcknowledgedMessageCount();

    protected abstract void incrementRejectedMessageCount();

    protected abstract void resetConnectionCount();
    protected abstract void resetChannelCount();
    protected abstract void resetPublishedMessageCount();
    protected abstract void resetConsumedMessageCount();
    protected abstract void resetAcknowledgedMessageCount();
    protected abstract void resetRejectedMessageCount();

    @Override
    public void newConnection(final Connection connection) {
        if(connection.getId() == null) {
            connection.setId(UUID.randomUUID().toString());
        }
        incrementConnectionCount();
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
            decrementConnectionCount();
        }
    }

    @Override
    public void newChannel(final Channel channel) {
        incrementChannelCount();
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
            decrementChannelCount();
        }
    }

    @Override
    public void basicPublish(Channel channel) {
        incrementPublishedMessageCount();
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
        incrementConsumedMessageCount();
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
        incrementConsumedMessageCount();
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
        updateChannelStateAfterAckReject(channel, deliveryTag, multiple, new Runnable() {
            @Override
            public void run() {
                incrementAcknowledgedMessageCount();
            }
        });
    }

    @Override
    public void basicNack(Channel channel, long deliveryTag) {
        updateChannelStateAfterAckReject(channel, deliveryTag, true, new Runnable() {
            @Override
            public void run() {
                incrementRejectedMessageCount();
            }
        });
    }

    @Override
    public void basicReject(Channel channel, long deliveryTag) {
        updateChannelStateAfterAckReject(channel, deliveryTag, false, new Runnable() {
            @Override
            public void run() {
                incrementRejectedMessageCount();
            }
        });
    }

    private void updateChannelStateAfterAckReject(Channel channel, long deliveryTag, boolean multiple, Runnable counterAction) {
        ChannelState channelState = channelState(channel);
        channelState.lock.lock();
        try {
            if(multiple) {
                Iterator<Long> iterator = channelState.unackedMessageDeliveryTags.iterator();
                while(iterator.hasNext()) {
                    long messageDeliveryTag = iterator.next();
                    if(messageDeliveryTag <= deliveryTag) {
                        iterator.remove();
                        counterAction.run();
                    }
                }
            } else {
                channelState.unackedMessageDeliveryTags.remove(deliveryTag);
                counterAction.run();
            }
        } finally {
            channelState.lock.unlock();
        }
    }

    @Override
    public void clear() {
        resetConnectionCount();
        resetChannelCount();
        resetPublishedMessageCount();
        resetConsumedMessageCount();
        resetAcknowledgedMessageCount();
        resetRejectedMessageCount();

        connectionState.clear();
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
                            decrementChannelCount();
                            LOGGER.info("Ripped off state of channel {} of connection {}. This is abnormal, please report.",
                                channel.getChannelNumber(), connection.getId());
                        }
                    }
                } else {
                    connectionStateIterator.remove();
                    decrementConnectionCount();
                    addToChannelCount(-connectionEntry.getValue().channelState.size());
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
