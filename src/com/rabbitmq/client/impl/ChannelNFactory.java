package com.rabbitmq.client.impl;

public interface ChannelNFactory {

    ChannelN instanciate(AMQConnection connection, int channelNumber, ConsumerWorkService workService);

}
