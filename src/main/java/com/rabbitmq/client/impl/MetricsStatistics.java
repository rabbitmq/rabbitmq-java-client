package com.rabbitmq.client.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class MetricsStatistics extends BaseStatistics {

    private final MetricRegistry registry;

    private final Counter connections;
    private final Counter channels;
    private final Meter publishedMessages;
    private final Meter consumedMessages;
    private final Meter acknowledgedMessages;
    private final Meter rejectedMessages;


    public MetricsStatistics(MetricRegistry registry) {
        this.registry = registry;
        this.connections = registry.counter("connections");
        this.channels = registry.counter("channels");
        this.publishedMessages = registry.meter("published");
        this.consumedMessages = registry.meter("consumed");
        this.acknowledgedMessages = registry.meter("acknowledged");
        this.rejectedMessages = registry.meter("rejected");
    }

    public MetricsStatistics() {
        this(new MetricRegistry());
    }

    @Override
    public long getConnectionCount() {
        return connections.getCount();
    }

    @Override
    public long getChannelCount() {
        return channels.getCount();
    }

    @Override
    public long getPublishedMessageCount() {
        return publishedMessages.getCount();
    }

    @Override
    public long getConsumedMessageCount() {
        return consumedMessages.getCount();
    }

    @Override
    public long getAcknowledgedMessageCount() {
        return acknowledgedMessages.getCount();
    }

    @Override
    public long getRejectedMessageCount() {
        return rejectedMessages.getCount();
    }

    @Override
    protected void incrementConnectionCount() {
        connections.inc();
    }

    @Override
    protected void decrementConnectionCount() {
        connections.dec();
    }

    @Override
    protected void incrementChannelCount() {
        channels.inc();
    }

    @Override
    protected void addToChannelCount(long nbChannel) {
        channels.inc(nbChannel);
    }

    @Override
    protected void decrementChannelCount() {
        channels.dec();
    }

    @Override
    protected void incrementPublishedMessageCount() {
        publishedMessages.mark();
    }

    @Override
    protected void incrementConsumedMessageCount() {
        consumedMessages.mark();
    }

    @Override
    protected void incrementAcknowledgedMessageCount() {
        acknowledgedMessages.mark();
    }

    @Override
    protected void incrementRejectedMessageCount() {
        rejectedMessages.mark();
    }

    @Override
    protected void resetConnectionCount() {
        // FIXME better reset for Metrics counters
        connections.dec(connections.getCount());
    }

    @Override
    protected void resetChannelCount() {
        // FIXME better reset for Metrics counters
        channels.dec(channels.getCount());
    }

    @Override
    protected void resetPublishedMessageCount() {
        // FIXME reset Metrics meter
    }

    @Override
    protected void resetConsumedMessageCount() {
        // FIXME reset Metrics meter
    }

    @Override
    protected void resetAcknowledgedMessageCount() {
        // FIXME reset Metrics meter
    }

    @Override
    protected void resetRejectedMessageCount() {
        // FIXME reset Metrics meter
    }

    public MetricRegistry getMetricRegistry() {
        return registry;
    }
}
