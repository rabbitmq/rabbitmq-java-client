package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;

import java.io.IOException;

public class RecoveryAwareAMQConnectionFactory {
    private ConnectionParams params;
    private FrameHandlerFactory factory;
    private Address[] addrs;

    public RecoveryAwareAMQConnectionFactory(ConnectionParams params, FrameHandlerFactory factory, Address[] addrs) {
        this.params = params;
        this.factory = factory;
        this.addrs = addrs;
    }

    /**
     * @return an interface to the connection
     * @throws IOException if it encounters a problem
     */
    RecoveryAwareAMQConnection newConnection() throws IOException
    {
        IOException lastException = null;
        for (Address addr : addrs) {
            try {
                FrameHandler frameHandler = factory.create(addr);
                RecoveryAwareAMQConnection conn = new RecoveryAwareAMQConnection(params, frameHandler);
                conn.start();
                return conn;
            } catch (IOException e) {
                lastException = e;
            }
        }

        return (RecoveryAwareAMQConnection) ConnectionFactory.rethrowOrIndicateConnectionFailure(lastException);
    }
}
