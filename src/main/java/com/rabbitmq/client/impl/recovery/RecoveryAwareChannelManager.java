package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelManager;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @since 3.3.0
 */
public class RecoveryAwareChannelManager extends ChannelManager {
    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax) {
        this(workService, channelMax, Executors.defaultThreadFactory());
    }

    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory) {
        super(workService, channelMax, threadFactory);
    }

    @Override
    protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new RecoveryAwareChannelN(connection, channelNumber, workService);
    }
}
