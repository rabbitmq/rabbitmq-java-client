package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class PerQueueVsPerMessageTTL extends PerMessageTTL {

    public void testSmallerPerQueueExpiryWins() throws IOException, InterruptedException {
        declareAndBindQueue(10);
        this.sessionTTL = 1000;

        publish("message1");

        Thread.sleep(100);

        assertNull("per-queue ttl should have removed message after 10ms!", get());
    }

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        final Object mappedTTL = (ttlValue instanceof String &&
                                    ((String) ttlValue).contains("foobar")) ?
                                    ttlValue : longValue(ttlValue) * 2;
        this.sessionTTL = ttlValue;
        Map<String, Object> argMap = Collections.singletonMap(PerQueueTTL.TTL_ARG, mappedTTL);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    private Long longValue(final Object ttl) {
        if (ttl instanceof Short) {
            return ((Short)ttl).longValue();
        } else if (ttl instanceof Integer) {
            return ((Integer)ttl).longValue();
        } else if (ttl instanceof Long) {
            return (Long) ttl;
        } else {
            throw new IllegalArgumentException("ttl not of expected type");
        }
    }

}
