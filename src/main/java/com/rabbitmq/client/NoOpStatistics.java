package com.rabbitmq.client;

/**
 *
 */
public class NoOpStatistics implements StatisticsCollector {

    @Override
    public void newConnection(Connection connection) {

    }

    @Override
    public void closeConnection(Connection connection) {

    }

    @Override
    public void newChannel(Channel channel) {

    }

    @Override
    public void closeChannel(Channel channel) {

    }

    @Override
    public void command(Connection connection, Channel channel, Command command) {

    }

    @Override
    public void basicPublish(Channel channel) {

    }

    @Override
    public void consumedMessage(Channel channel) {

    }

    @Override
    public void clear() {

    }

    @Override
    public long getConnectionCount() {
        return 0;
    }

    @Override
    public long getChannelCount() {
        return 0;
    }

    @Override
    public long getPublishedMessageCount() {
        return 0;
    }

    @Override
    public long getConsumedMessageCount() {
        return 0;
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
