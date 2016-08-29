package com.rabbitmq.client;

/**
 *
 */
public interface Statistics {

    void clear();

    long getConnectionCount();

    long getChannelCount();

    long getPublishedMessageCount();

    long getConsumedMessageCount();

    long getAcknowledgedMessageCount();

    long getRejectedMessageCount();

}
