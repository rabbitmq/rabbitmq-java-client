package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ChannelNFactory;
import com.rabbitmq.client.impl.ConsumerWorkService;

public class RecoveryAwareChannelNFactory implements ChannelNFactory {
    public ChannelN instanciate(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new RecoveryAwareChannelN(connection, channelNumber, workService);
    }
}
