package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelManager;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;

/**
 * @since 3.3.0
 */
public class RecoveryAwareChannelManager extends ChannelManager {
    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax) {
        super(workService, channelMax);
    }

    @Override
    protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new RecoveryAwareChannelN(connection, channelNumber, workService);
    }
}
