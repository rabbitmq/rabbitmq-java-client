package com.rabbitmq.client;

/**
 *
 */
public interface StatisticsCollector extends Statistics {

    void newConnection(Connection connection);

    void closeConnection(Connection connection);

    void newChannel(Channel channel);

    void closeChannel(Channel channel);

    void basicPublish(Channel channel);

    void consumedMessage(Channel channel);

    void command(Connection connection, Channel channel, Command command);

}
