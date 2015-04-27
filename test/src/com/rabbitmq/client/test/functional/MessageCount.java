package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class MessageCount extends BrokerTestCase {
    public void testMessageCount() throws IOException {
        String q = generateQueueName();
        channel.queueDeclare(q, false, true, false, null);
        assertEquals(0, channel.messageCount(q));

        basicPublishVolatile(q);
        assertEquals(1, channel.messageCount(q));
        basicPublishVolatile(q);
        assertEquals(2, channel.messageCount(q));

        channel.queuePurge(q);
        assertEquals(0, channel.messageCount(q));
    }
}
