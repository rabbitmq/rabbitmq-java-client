package com.rabbitmq.client.impl;

import com.rabbitmq.client.Address;

import java.io.IOException;

/**
 *
 */
public interface FrameHandlerFactory {

    FrameHandler create(Address addr, String connectionName) throws IOException;

}
