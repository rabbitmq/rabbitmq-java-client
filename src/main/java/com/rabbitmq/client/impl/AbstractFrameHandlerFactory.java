package com.rabbitmq.client.impl;

import com.rabbitmq.client.SocketConfigurator;

/**
 *
 */
public abstract class AbstractFrameHandlerFactory implements FrameHandlerFactory {

    protected final int connectionTimeout;
    protected final SocketConfigurator configurator;
    protected final boolean ssl;

    protected AbstractFrameHandlerFactory(int connectionTimeout, SocketConfigurator configurator, boolean ssl) {
        this.connectionTimeout = connectionTimeout;
        this.configurator = configurator;
        this.ssl = ssl;
    }
}
