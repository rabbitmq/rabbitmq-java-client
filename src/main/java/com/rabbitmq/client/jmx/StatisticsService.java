package com.rabbitmq.client.jmx;

import com.rabbitmq.client.Statistics;
import com.rabbitmq.client.impl.ConcurrentStatistics;

/**
 *
 */
public class StatisticsService implements StatisticsServiceMBean {

    private final Statistics delegate;

    public StatisticsService() {
        this(new ConcurrentStatistics());
    }

    public StatisticsService(Statistics delegate) {
        this.delegate = delegate;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public long getConnectionCount() {
        return delegate.getConnectionCount();
    }

    @Override
    public long getChannelCount() {
        return delegate.getChannelCount();
    }

    @Override
    public long getPublishedMessageCount() {
        return delegate.getPublishedMessageCount();
    }

    @Override
    public long getConsumedMessageCount() {
        return delegate.getConsumedMessageCount();
    }

    @Override
    public long getAcknowledgedMessageCount() {
        return delegate.getAcknowledgedMessageCount();
    }

    @Override
    public long getRejectedMessageCount() {
        return delegate.getRejectedMessageCount();
    }
}
