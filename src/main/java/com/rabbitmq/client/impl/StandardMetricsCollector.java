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

package com.rabbitmq.client.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dropwizard Metrics implementation of {@link MetricsCollector}.
 * Note transactions are not supported (see {@link MetricsCollector}.
 * Metrics provides out-of-the-box support for report backends like JMX,
 * Graphite, Ganglia, or plain HTTP. See Metrics documentation for
 * more details.
 *
 * @see MetricsCollector
 */
public class StandardMetricsCollector implements MetricsCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardMetricsCollector.class);

    private final ConcurrentMap<String, ConnectionState> connectionState = new ConcurrentHashMap<String, ConnectionState>();

    private final MetricRegistry registry;

    private final Counter connections;
    private final Counter channels;
    private final Meter publishedMessages;
    private final Meter consumedMessages;
    private final Meter acknowledgedMessages;
    private final Meter rejectedMessages;


    public StandardMetricsCollector(MetricRegistry registry, String metricsPrefix) {
        this.registry = registry;
        this.connections = registry.counter(metricsPrefix+".connections");
        this.channels = registry.counter(metricsPrefix+".channels");
        this.publishedMessages = registry.meter(metricsPrefix+".published");
        this.consumedMessages = registry.meter(metricsPrefix+".consumed");
        this.acknowledgedMessages = registry.meter(metricsPrefix+".acknowledged");
        this.rejectedMessages = registry.meter(metricsPrefix+".rejected");
    }

    public StandardMetricsCollector() {
        this(new MetricRegistry());
    }
    
    public StandardMetricsCollector(MetricRegistry metricRegistry) {
        this(metricRegistry, "rabbitmq");
    }

    @Override
    public void newConnection(final Connection connection) {
        try {
            if(connection.getId() == null) {
                connection.setId(UUID.randomUUID().toString());
            }
            connections.inc();
            connectionState.put(connection.getId(), new ConnectionState(connection));
            connection.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    closeConnection(connection);
                }
            });
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in newConnection: " + e.getMessage());
        }
    }

    @Override
    public void closeConnection(Connection connection) {
        try {
            ConnectionState removed = connectionState.remove(connection.getId());
            if(removed != null) {
                connections.dec();
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in closeConnection: " + e.getMessage());
        }
    }

    @Override
    public void newChannel(final Channel channel) {
        try {
            channels.inc();
            channel.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    closeChannel(channel);
                }
            });
            connectionState(channel.getConnection()).channelState.put(channel.getChannelNumber(), new ChannelState(channel));
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in newChannel: " + e.getMessage());
        }
    }

    @Override
    public void closeChannel(Channel channel) {
        try {
            ChannelState removed = connectionState(channel.getConnection()).channelState.remove(channel.getChannelNumber());
            if(removed != null) {
                channels.dec();
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in closeChannel: " + e.getMessage());
        }
    }

    @Override
    public void basicPublish(Channel channel) {
        try {
            publishedMessages.mark();
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicPublish: " + e.getMessage());
        }
    }

    @Override
    public void basicConsume(Channel channel, String consumerTag, boolean autoAck) {
        try {
            if(!autoAck) {
                ChannelState channelState = channelState(channel);
                channelState.lock.lock();
                try {
                    channelState(channel).consumersWithManualAck.add(consumerTag);
                } finally {
                    channelState.lock.unlock();
                }
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicConsume: " + e.getMessage());
        }
    }

    @Override
    public void basicCancel(Channel channel, String consumerTag) {
        try {
            ChannelState channelState = channelState(channel);
            channelState.lock.lock();
            try {
                channelState(channel).consumersWithManualAck.remove(consumerTag);
            } finally {
                channelState.lock.unlock();
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicCancel: " + e.getMessage());
        }
    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, boolean autoAck) {
        try {
            consumedMessages.mark();
            if(!autoAck) {
                ChannelState channelState = channelState(channel);
                channelState.lock.lock();
                try {
                    channelState(channel).unackedMessageDeliveryTags.add(deliveryTag);
                } finally {
                    channelState.lock.unlock();
                }
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in consumedMessage: " + e.getMessage());
        }
    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, String consumerTag) {
        try {
            consumedMessages.mark();
            ChannelState channelState = channelState(channel);
            channelState.lock.lock();
            try {
                if(channelState.consumersWithManualAck.contains(consumerTag)) {
                    channelState.unackedMessageDeliveryTags.add(deliveryTag);
                }
            } finally {
                channelState.lock.unlock();
            }
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in consumedMessage: " + e.getMessage());
        }
    }

    @Override
    public void basicAck(Channel channel, long deliveryTag, boolean multiple) {
        try {
            updateChannelStateAfterAckReject(channel, deliveryTag, multiple, acknowledgedMessages);
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicAck: " + e.getMessage());
        }
    }

    @Override
    public void basicNack(Channel channel, long deliveryTag) {
        try {
            updateChannelStateAfterAckReject(channel, deliveryTag, true, rejectedMessages);
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicNack: " + e.getMessage());
        }
    }

    @Override
    public void basicReject(Channel channel, long deliveryTag) {
        try {
            updateChannelStateAfterAckReject(channel, deliveryTag, false, rejectedMessages);
        } catch(Exception e) {
            LOGGER.info("Error while computing metrics in basicReject: " + e.getMessage());
        }
    }

    private void updateChannelStateAfterAckReject(Channel channel, long deliveryTag, boolean multiple, Meter meter) {
        ChannelState channelState = channelState(channel);
        channelState.lock.lock();
        try {
            if(multiple) {
                Iterator<Long> iterator = channelState.unackedMessageDeliveryTags.iterator();
                while(iterator.hasNext()) {
                    long messageDeliveryTag = iterator.next();
                    if(messageDeliveryTag <= deliveryTag) {
                        iterator.remove();
                        meter.mark();
                    }
                }
            } else {
                channelState.unackedMessageDeliveryTags.remove(deliveryTag);
                meter.mark();
            }
        } finally {
            channelState.lock.unlock();
        }
    }

    private ConnectionState connectionState(Connection connection) {
        return connectionState.get(connection.getId());
    }

    private ChannelState channelState(Channel channel) {
        return connectionState(channel.getConnection()).channelState.get(channel.getChannelNumber());
    }

    /**
     * Clean inner state for close connections and channels.
     * Inner state is automatically cleaned on connection
     * and channel closing.
     * Thus, this method is provided as a safety net, to be externally
     * called periodically if closing of resources wouldn't work
     * properly for some corner cases.
     */
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
                            channels.dec();
                            LOGGER.info("Ripped off state of channel {} of connection {}. This is abnormal, please report.",
                                channel.getChannelNumber(), connection.getId());
                        }
                    }
                } else {
                    connectionStateIterator.remove();
                    connections.dec();
                    channels.dec(connectionEntry.getValue().channelState.size());
                    LOGGER.info("Ripped off state of connection {}. This is abnormal, please report.",
                        connection.getId());
                }
            }
        } catch(Exception e) {
            LOGGER.info("Error during periodic clean of metricsCollector: "+e.getMessage());
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
    
    public MetricRegistry getMetricRegistry() {
        return registry;
    }

    public Counter getConnections() {
        return connections;
    }

    public Counter getChannels() {
        return channels;
    }

    public Meter getPublishedMessages() {
        return publishedMessages;
    }

    public Meter getConsumedMessages() {
        return consumedMessages;
    }

    public Meter getAcknowledgedMessages() {
        return acknowledgedMessages;
    }

    public Meter getRejectedMessages() {
        return rejectedMessages;
    }
}
