package com.rabbitmq.client.test.server;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.functional.ClusteredTestBase;

import java.io.IOException;

/**
 * From bug 19844 - we want to be sure that publish vs everything else can't
 * happen out of order
 */
public class EffectVisibilityCrossNodeTest extends ClusteredTestBase {
    private static final String exchange = "exchange";

    private String[] queues = new String[QUEUES];

    @Override
    protected void createResources() throws IOException {
        channel.exchangeDeclare(exchange, "fanout");

        for (int i = 0; i < queues.length ; i++) {
            queues[i] = alternateChannel.queueDeclare().getQueue();
            alternateChannel.queueBind(queues[i], exchange, "");
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.exchangeDelete(exchange);
    }

    private static final int QUEUES = 5;
    private static final int COMMITS = 500;
    private static final int MESSAGES_PER_COMMIT = 10;

    public void testEffectVisibility() throws Exception {
        channel.txSelect();

        for (int i = 0; i < COMMITS; i++) {
            for (int j = 0; j < MESSAGES_PER_COMMIT; j++) {
                channel.basicPublish(exchange, "", MessageProperties.MINIMAL_BASIC, ("" + (i * MESSAGES_PER_COMMIT + j)).getBytes());
            }
            channel.txCommit();

            for (int j = 0; j < MESSAGES_PER_COMMIT; j++) {
                channel.basicPublish(exchange, "", MessageProperties.MINIMAL_BASIC, "bad".getBytes());
            }
            channel.txRollback();
        }

        for (int i = 0; i < queues.length ; i++) {
            QueueingConsumer consumer = new QueueingConsumer(alternateChannel);
            alternateChannel.basicConsume(queues[i], true, consumer);

            for (int j = 0; j < MESSAGES_PER_COMMIT * COMMITS; j++) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery(1000);
                assertNotNull(delivery);
                int sequence = Integer.parseInt(new String(delivery.getBody()));

                assertEquals(j, sequence);
            }
        }
    }
}
