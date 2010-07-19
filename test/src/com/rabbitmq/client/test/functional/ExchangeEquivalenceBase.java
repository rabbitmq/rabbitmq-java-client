package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Map;

public abstract class ExchangeEquivalenceBase extends BrokerTestCase {
    public void verifyEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        channel.exchangeDeclare(name, type, durable, autoDelete, args);
    }

    // Note: this will close the channel
    public void verifyNotEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        try {
            channel.exchangeDeclare(name, type, durable, autoDelete, args);
            fail("Exchange was supposed to be not equivalent");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_ALLOWED, ioe);
            return;
        }
    }
}
