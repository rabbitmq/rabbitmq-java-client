package com.rabbitmq.client.impl;

public class DefaultChannelNFactory implements ChannelNFactory {
    public ChannelN instanciate(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new ChannelN(connection, channelNumber, workService);
    }
}
