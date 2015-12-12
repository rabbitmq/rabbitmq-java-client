package com.rabbitmq.client.test.server;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PriorityQueues extends BrokerTestCase {
    public void testPrioritisingBasics() throws IOException, TimeoutException, InterruptedException {
        String q = "with-3-priorities";
        int n = 3;
        channel.queueDeclare(q, true, false, false, argsWithPriorities(n));
        publishWithPriorities(q, n);

        List<Integer> xs = prioritiesOfEnqueuedMessages(q, n);
        assertEquals(Integer.valueOf(3), xs.get(0));
        assertEquals(Integer.valueOf(2), xs.get(1));
        assertEquals(Integer.valueOf(1), xs.get(2));

        channel.queueDelete(q);
    }

    private List<Integer> prioritiesOfEnqueuedMessages(String q, int n) throws InterruptedException, IOException {
        final List<Integer> xs = new ArrayList<Integer>();
        final CountDownLatch latch = new CountDownLatch(n);

        channel.basicConsume(q, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                xs.add(properties.getPriority());
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        return xs;
    }

    private void publishWithPriorities(String q, int n) throws IOException, TimeoutException, InterruptedException {
        channel.confirmSelect();
        for (int i = 1; i <= n; i++) {
            channel.basicPublish("", q, propsWithPriority(i), "msg".getBytes("UTF-8"));
        }
        channel.waitForConfirms(500);
    }

    private AMQP.BasicProperties propsWithPriority(int n) {
        return (new AMQP.BasicProperties.Builder())
                .priority(n)
                .build();
    }

    private Map<String, Object> argsWithPriorities(int n) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("x-max-priority", n);
        return m;
    }
}
