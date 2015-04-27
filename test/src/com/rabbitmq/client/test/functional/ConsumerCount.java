package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class ConsumerCount extends BrokerTestCase {
    public void testConsumerCount() throws IOException {
        String q = generateQueueName();
        channel.queueDeclare(q, false, true, false, null);
        assertEquals(0, channel.consumerCount(q));

        String tag = channel.basicConsume(q, new DefaultConsumer(channel));
        assertEquals(1, channel.consumerCount(q));

        channel.basicCancel(tag);
        assertEquals(0, channel.consumerCount(q));
    }
}
