package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * Test that channel flow applies even when there are as yet no consumers.
 */
public class ChannelFlowNoConsumersTest extends BrokerTestCase {
    public void test() throws Exception {
        String q = channel.queueDeclare("", false, true, false, null).getQueue();
        basicPublishVolatile(q);
        channel.flow(false);
        String tag = channel.basicConsume(q, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                throw new IOException("Message received after channel flow.");
            }
        });

        Thread.sleep(1000);
        channel.basicCancel(tag);
    }
}
