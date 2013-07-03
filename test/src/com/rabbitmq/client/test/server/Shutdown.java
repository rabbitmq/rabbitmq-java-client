package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;

public class Shutdown extends BrokerTestCase {

    public void testErrorOnShutdown() throws Exception {
        bareRestart();
        expectError(AMQP.CONNECTION_FORCED);
    }

}
