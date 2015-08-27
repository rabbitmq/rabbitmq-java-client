package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.impl.*;

/**
 * {@link com.rabbitmq.client.impl.AMQConnection} modification that uses {@link com.rabbitmq.client.impl.recovery.RecoveryAwareChannelN}
 *
 * @since 3.3.0
 */
public class RecoveryAwareAMQConnection extends AMQConnection {
    public RecoveryAwareAMQConnection(ConnectionParams params, FrameHandler handler) {
        super(params, handler);
        setChannelManagerFactory(new ChannelManagerFactory() {
            public ChannelManager instantiateChannelManager(int channelMax) {
                return new ChannelManager(_workService, channelMax, getShutdownThreadPoolExecutor(), new RecoveryAwareChannelNFactory());
            }
        });
    }
}
