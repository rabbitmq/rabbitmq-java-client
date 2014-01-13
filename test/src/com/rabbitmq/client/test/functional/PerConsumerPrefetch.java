package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static com.rabbitmq.client.test.functional.QosTests.drain;

public class PerConsumerPrefetch extends BrokerTestCase {
    private String q;

    @Override
    protected void createResources() throws IOException {
        q = channel.queueDeclare().getQueue();
    }

    public void testPrefetch() throws IOException {
        QueueingConsumer c = new QueueingConsumer(channel);
        publish(q, 5);
        consume(c, false);
        Queue<Delivery> dels = drain(c, 2);
        for (Delivery del : dels) {
            ack(del, false);
        }
        Delivery last = drain(c, 2).getLast();
        ack(last, true);
        drain(c, 1);
        publish(q, 5);
        drain(c, 1);
    }

    public void testAutoAckIgnoresPrefetch() throws IOException {
        QueueingConsumer c = new QueueingConsumer(channel);
        publish(q, 10);
        consume(c, true);
        drain(c, 10);
    }

    private void publish(String q, int n) throws IOException {
        for (int i = 0; i < n; i++) {
            channel.basicPublish("", q, null, "".getBytes());
        }
    }

    private void consume(QueueingConsumer c, boolean autoAck) throws IOException {
        channel.basicConsume(q, autoAck, "", false, false, args(2), c);
    }

    private void ack(Delivery del, boolean multi) throws IOException {
        channel.basicAck(del.getEnvelope().getDeliveryTag(), multi);
    }

    private Map<String, Object> args(int prefetch) {
        Map<String, Object> a = new HashMap<String, Object>();
        if (prefetch != 0) {
            a.put("x-prefetch", prefetch);
        }
        return a;
    }
}
