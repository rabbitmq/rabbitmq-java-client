package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class PerQueueVsPerMessageTTL extends PerMessageTTL {

    public void testSmallerPerQueueExpiryWins() throws IOException, InterruptedException {
        declareAndBindQueue(10);

        sessionTTL = 1000;
        publish("message1");

        Thread.sleep(10);

        assertNull("per-queue ttl should have removed message after 10ms!", get());
    }

    public void testSmallerPerMessageExpiryWins() throws IOException, InterruptedException {
        declareAndBindQueue(5000);

        sessionTTL = 10;
        publish("message2");

        Thread.sleep(1000);

        assertNull("per-message ttl should have removed message after 10ms!", get());
    }

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        this.sessionTTL = ttlValue;
        Map<String, Object> argMap = Collections.singletonMap(PerQueueTTL.TTL_ARG, ttlValue);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }
}
