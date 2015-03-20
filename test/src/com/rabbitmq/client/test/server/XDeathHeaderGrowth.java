package com.rabbitmq.client.test.server;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class RejectingConsumer extends DefaultConsumer {
    private CountDownLatch latch;
    private Map<String, Object> headers;

    public RejectingConsumer(Channel channel, CountDownLatch latch) {
        super(channel);
        this.latch = latch;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body)
            throws IOException {
        if(this.latch.getCount() > 0) {
            this.getChannel().basicReject(envelope.getDeliveryTag(), false);
        } else {
            this.getChannel().basicAck(envelope.getDeliveryTag(), false);
        }
        this.headers = properties.getHeaders();
        latch.countDown();
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }
}

public class XDeathHeaderGrowth extends BrokerTestCase {
    public void testBoundedXDeathHeaderGrowth() throws IOException, InterruptedException {
        final String x1 = "issues.rabbitmq-server-78.fanout1";
        declareTransientFanoutExchange(x1);
        final String x2 = "issues.rabbitmq-server-78.fanout2";
        declareTransientFanoutExchange(x2);
        final String x3 = "issues.rabbitmq-server-78.fanout3";
        declareTransientFanoutExchange(x3);

        final String q1 = "issues.rabbitmq-server-78.queue1";
        Map<String, Object> args1 = argumentsForDeadLetteringTo(x1);
        declareTransientQueue(q1, args1);

        final String q2 = "issues.rabbitmq-server-78.queue2";
        Map<String, Object> args2 = argumentsForDeadLetteringTo(x2);
        declareTransientQueue(q2, args2);
        this.channel.queueBind(q2, x1, "");

        final String q3 = "issues.rabbitmq-server-78.queue3";
        Map<String, Object> args3 = argumentsForDeadLetteringTo(x3);
        declareTransientQueue(q3, args3);
        this.channel.queueBind(q3, x2, "");

        final String qz = "issues.rabbitmq-server-78.destination";
        Map<String, Object> args4 = argumentsForDeadLetteringTo(x3);
        declareTransientQueue(qz, args4);
        this.channel.queueBind(qz, x3, "");

        CountDownLatch latch = new CountDownLatch(5);
        RejectingConsumer cons = new RejectingConsumer(this.channel, latch);
        this.channel.basicConsume(qz, cons);

        this.channel.basicPublish("", q1, null, "msg".getBytes());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        List<Map<String, Object>> events = (List<Map<String, Object>>)cons.getHeaders().get("x-death");
        assertEquals(4, events.size());

        List<String> qs = new ArrayList<String>();
        for (Map<String, Object> evt : events) {
            qs.add(evt.get("queue").toString());
        }
        Collections.sort(qs);
        assertEquals(Arrays.asList(qz, q1, q2, q3), qs);
    }

    private Map<String, Object> argumentsForDeadLetteringTo(String dlx) {
        return argumentsForDeadLetteringTo(dlx, 1);
    }

    private Map<String, Object> argumentsForDeadLetteringTo(String dlx, int ttl) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("x-dead-letter-exchange", dlx);
        m.put("x-dead-letter-routing-key", "some-routing-key");
        m.put("x-message-ttl", ttl);
        return m;
    }
}
