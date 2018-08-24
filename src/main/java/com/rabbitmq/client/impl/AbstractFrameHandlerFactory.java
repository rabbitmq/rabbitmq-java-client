package com.rabbitmq.client.impl;

import com.rabbitmq.client.ConnectionContext;
import com.rabbitmq.client.ConnectionPostProcessor;
import com.rabbitmq.client.SocketConfigurator;

import java.io.IOException;

/**
 *
 */
public abstract class AbstractFrameHandlerFactory implements FrameHandlerFactory {

    protected final int connectionTimeout;
    protected final SocketConfigurator configurator;
    protected final boolean ssl;
    protected final ConnectionPostProcessor connectionPostProcessor;

    protected AbstractFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, boolean ssl, ConnectionPostProcessor connectionPostProcessor) {
        this.connectionTimeout = connectionTimeout;
        this.configurator = configurator;
        this.ssl = ssl;
        this.connectionPostProcessor = connectionPostProcessor == null ? new ConnectionPostProcessor() {

            @Override
            public void postProcess(ConnectionContext context) {

            }
        } : connectionPostProcessor;
    }
}
