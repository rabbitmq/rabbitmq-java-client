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
    public void basicAck(Channel channel, long deliveryTag, boolean multiple) {

    }

    @Override
    public void basicConsume(Channel channel, String consumerTag, boolean autoAck) {

    }

    @Override
    public void basicPublish(Channel channel) {

    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, boolean autoAck) {

    }

    @Override
    public void consumedMessage(Channel channel, long deliveryTag, String consumerTag) {

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
