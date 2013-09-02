package com.rabbitmq.client.test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

public class QueueingConsumerMultiTests extends BrokerTestCase {
    static final String QUEUE1 = "some-queue1";
    static final String QUEUE2 = "some-queue2";
    static final String QUEUE3 = "some-queue3";

    public void test3ConsumersWithInterleavingCancelNotifications() throws Exception {
        final Channel channel = connection.createChannel();
        final QueueingConsumer c = new QueueingConsumer(channel);
        for (String q : asList(QUEUE1, QUEUE2, QUEUE3)) {
            channel.queueDeclare(q, false, false, true, null);
            channel.basicConsume(q, true, c);
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final BlockingQueue<QueueingConsumer.Delivery> result = new ArrayBlockingQueue<QueueingConsumer.Delivery>(7);
        new Thread() {
            @Override
            public void run() {
                try {
                    QueueingConsumer.Delivery delivery;
                    while ((delivery = c.nextDelivery(1000)) != null) {
                        result.add(delivery);
                    }
                } catch (Exception ex) {
                    // we don't care about *any* types of failure,
                    // since the result queue length is our only test
                } finally {
                    latch.countDown();
                }
            }
        }.start();

        final Thread publisher = new Thread() {
            @Override
            public void run() {
                for (int i : asList(1, 2, 3)) {
                    for (String q : asList(QUEUE1, QUEUE2, QUEUE3)) {
                        if (i == 2 && q == QUEUE2) { // remove q2 early, so 2 messages aren't delivered
                            try {
                                channel.queueDelete(QUEUE2);
                            } catch (IOException e) {
                                break;
                            }
                        } else {
                            try {
                                basicPublishVolatile(q);
                            } catch (IOException e) { }
                        }
                    }
                }
            }
        };
        publisher.start();
        publisher.join();

        // Far longer than this could reasonably take
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("expected 7 deliveries", 7, result.size());
    }

}
