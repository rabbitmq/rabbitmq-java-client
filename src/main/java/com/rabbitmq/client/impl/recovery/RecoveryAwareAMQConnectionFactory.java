package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class RecoveryAwareAMQConnectionFactory {
    private final ConnectionParams params;
    private final FrameHandlerFactory factory;
    private final List<Address> addrs;

    public RecoveryAwareAMQConnectionFactory(ConnectionParams params, FrameHandlerFactory factory, List<Address> addrs) {
        this.params = params;
        this.factory = factory;
        this.addrs = addrs;
    }

    /**
     * @return an interface to the connection
     * @throws java.io.IOException if it encounters a problem
     */
    RecoveryAwareAMQConnection newConnection() throws IOException, TimeoutException {
        IOException lastException = null;
        List<Address> shuffled = shuffle(addrs);

        for (Address addr : shuffled) {
            try {
                FrameHandler frameHandler = factory.create(addr);
                RecoveryAwareAMQConnection conn = new RecoveryAwareAMQConnection(params, frameHandler);
                conn.start();
                return conn;
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw (lastException != null) ? lastException : new IOException("failed to connect");
    }

    private List<Address> shuffle(List<Address> addrs) {
        List<Address> list = new ArrayList<Address>(addrs);
        Collections.shuffle(list);
        return list;
    }
}
