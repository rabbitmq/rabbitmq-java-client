package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelManager;
import com.rabbitmq.client.impl.ExceptionHandler;
import com.rabbitmq.client.impl.FrameHandler;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * {@link com.rabbitmq.client.impl.AMQConnection} modification that uses {@link com.rabbitmq.client.impl.recovery.RecoveryAwareChannelN}
 * @since 3.3.0
 */
public class RecoveryAwareAMQConnection extends AMQConnection {
    public RecoveryAwareAMQConnection(String username,
                                      String password,
                                      FrameHandler frameHandler,
                                      ExecutorService executor,
                                      String virtualHost,
                                      Map<String, Object> clientProperties,
                                      int requestedFrameMax,
                                      int requestedChannelMax,
                                      int requestedHeartbeat,
                                      SaslConfig saslConfig) {
        super(username,
              password,
              frameHandler,
              executor,
              virtualHost,
              clientProperties,
              requestedFrameMax,
              requestedChannelMax,
              requestedHeartbeat,
              saslConfig);
    }

    public RecoveryAwareAMQConnection(String username,
                                      String password,
                                      FrameHandler frameHandler,
                                      ExecutorService executor,
                                      String virtualHost,
                                      Map<String, Object> clientProperties,
                                      int requestedFrameMax,
                                      int requestedChannelMax,
                                      int requestedHeartbeat,
                                      SaslConfig saslConfig,
                                      ExceptionHandler execeptionHandler) {
        super(username,
              password,
              frameHandler,
              executor,
              virtualHost,
              clientProperties,
              requestedFrameMax,
              requestedChannelMax,
              requestedHeartbeat,
              saslConfig,
              execeptionHandler);
    }

    @Override
    protected RecoveryAwareChannelManager instantiateChannelManager(int channelMax) {
        return new RecoveryAwareChannelManager(super._workService, channelMax);
    }
}
