package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;

import java.util.concurrent.ThreadFactory;

/**
 * {@link com.rabbitmq.client.impl.AMQConnection} modification that uses {@link com.rabbitmq.client.impl.recovery.RecoveryAwareChannelN}
 * @since 3.3.0
 */
public class RecoveryAwareAMQConnection extends AMQConnection {
    public RecoveryAwareAMQConnection(ConnectionParams params, FrameHandler handler) {
        super(params, handler);
    }

    @Override
    protected RecoveryAwareChannelManager instantiateChannelManager(int channelMax, ThreadFactory threadFactory) {
        return new RecoveryAwareChannelManager(super._workService, channelMax, threadFactory);
    }
}
