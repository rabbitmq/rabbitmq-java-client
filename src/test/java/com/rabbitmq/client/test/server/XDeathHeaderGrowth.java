// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;

class RejectingConsumer extends DefaultConsumer {
    private CountDownLatch latch;
    private Map<String, Object> headers;
    private AtomicLong counter;

    public RejectingConsumer(Channel channel, CountDownLatch latch) {
        super(channel);
        this.latch = latch;
        this.counter = new AtomicLong(latch.getCount());
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body)
            throws IOException {

        if(this.latch.getCount() > 0) {
            this.getChannel().basicReject(envelope.getDeliveryTag(), false);
        } else {
            if(this.getChannel().isOpen()) {
                this.getChannel().basicAck(envelope.getDeliveryTag(), false);
            }
        }
        if(this.counter.decrementAndGet() == 0) {
            // get headers only when the message has been redelivered
            // the expected number of times.
            // it looks like the message can be redelivered because
            // of the reject when handleDelivery isn't done yet or
            // before the latch releases the main thread. There's then
            // an additional delivery and the checks (transiently) fail.
            this.headers = properties.getHeaders();
        }
        latch.countDown();
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }
}

public class XDeathHeaderGrowth extends BrokerTestCase {
    @SuppressWarnings("unchecked")
    @Test public void boundedXDeathHeaderGrowth() throws IOException, InterruptedException {
        final String x1 = "issues.rabbitmq-server-78.fanout1";
        declareTransientFanoutExchange(x1);
        final String x2 = "issues.rabbitmq-server-78.fanout2";
        declareTransientFanoutExchange(x2);
        final String x3 = "issues.rabbitmq-server-78.fanout3";
        declareTransientFanoutExchange(x3);

        final String q1 = "issues.rabbitmq-server-78.queue1";
        declareTransientQueue(q1, argumentsForDeadLetteringTo(x1));

        final String q2 = "issues.rabbitmq-server-78.queue2";
        declareTransientQueue(q2, argumentsForDeadLetteringTo(x2));
        this.channel.queueBind(q2, x1, "");

        final String q3 = "issues.rabbitmq-server-78.queue3";
        declareTransientQueue(q3, argumentsForDeadLetteringTo(x3));
        this.channel.queueBind(q3, x2, "");

        final String qz = "issues.rabbitmq-server-78.destination";
        declareTransientQueue(qz, argumentsForDeadLetteringWithoutTtlTo(x3));
        this.channel.queueBind(qz, x3, "");

        CountDownLatch latch = new CountDownLatch(10);
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
        List<Long> cs = new ArrayList<Long>();
        for (Map<String, Object> evt : events) {
            cs.add((Long)evt.get("count"));
        }
        Collections.sort(cs);
        assertEquals(Arrays.asList(1L, 1L, 1L, 9L), cs);

        cleanUpExchanges(x1, x2, x3);
        cleanUpQueues(q1, q2, q3, qz);
    }

    private void cleanUpExchanges(String... xs) throws IOException {
        for(String x : xs) {
            this.channel.exchangeDelete(x);
        }
    }
    private void cleanUpQueues(String... qs) throws IOException {
        for(String q : qs) {
            this.channel.queueDelete(q);
        }
    }

    @SuppressWarnings("unchecked")
    @Test public void handlingOfXDeathHeadersFromEarlierVersions() throws IOException, InterruptedException {
        final String x1 = "issues.rabbitmq-server-152.fanout1";
        declareTransientFanoutExchange(x1);
        final String x2 = "issues.rabbitmq-server-152.fanout2";
        declareTransientFanoutExchange(x2);

        final String q1 = "issues.rabbitmq-server-152.queue1";
        declareTransientQueue(q1, argumentsForDeadLetteringTo(x1));

        final String q2 = "issues.rabbitmq-server-152.queue2";
        declareTransientQueue(q2, argumentsForDeadLetteringTo(x2));
        this.channel.queueBind(q2, x1, "");

        final String qz = "issues.rabbitmq-server-152.destination";
        declareTransientQueue(qz, argumentsForDeadLetteringWithoutTtlTo(x2));
        this.channel.queueBind(qz, x2, "");

        CountDownLatch latch = new CountDownLatch(10);
        RejectingConsumer cons = new RejectingConsumer(this.channel, latch);
        this.channel.basicConsume(qz, cons);

        final AMQP.BasicProperties.Builder bldr = new AMQP.BasicProperties.Builder();
        AMQP.BasicProperties props = bldr.headers(
          propsWithLegacyXDeathsInHeaders("issues.rabbitmq-server-152.queue97",
                                          "issues.rabbitmq-server-152.queue97",
                                          "issues.rabbitmq-server-152.queue97",
                                          "issues.rabbitmq-server-152.queue98",
                                          "issues.rabbitmq-server-152.queue99")).build();
        this.channel.basicPublish("", q1, props, "msg".getBytes());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        List<Map<String, Object>> events = (List<Map<String, Object>>)cons.getHeaders().get("x-death");
        assertEquals(6, events.size());

        List<String> qs = new ArrayList<String>();
        for (Map<String, Object> evt : events) {
            qs.add(evt.get("queue").toString());
        }
        Collections.sort(qs);
        assertEquals(Arrays.asList(qz, q1, q2,
                                   "issues.rabbitmq-server-152.queue97",
                                   "issues.rabbitmq-server-152.queue98",
                                   "issues.rabbitmq-server-152.queue99"), qs);
        List<Long> cs = new ArrayList<Long>();
        for (Map<String, Object> evt : events) {
            cs.add((Long)evt.get("count"));
        }
        Collections.sort(cs);
        assertEquals(Arrays.asList(1L, 1L, 4L, 4L, 9L, 12L), cs);

        cleanUpExchanges(x1, x2);
        cleanUpQueues(q1, q2, qz,
                      "issues.rabbitmq-server-152.queue97",
                      "issues.rabbitmq-server-152.queue98",
                      "issues.rabbitmq-server-152.queue99");
    }

    private Map<String, Object> propsWithLegacyXDeathsInHeaders(String... qs) {
        Map<String, Object> m = new HashMap<String, Object>();
        List<Map<String, Object>> xDeaths = new ArrayList<Map<String, Object>>();
        for(String q : qs) {
            xDeaths.add(newXDeath(q));
            xDeaths.add(newXDeath(q));
            xDeaths.add(newXDeath(q));
            xDeaths.add(newXDeath(q));
        }

        m.put("x-death", xDeaths);
        return m;
    }

    private Map<String, Object> newXDeath(String q) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("reason", "expired");
        m.put("queue", q);
        m.put("exchange", "issues.rabbitmq-server-152.fanout0");
        m.put("routing-keys", Arrays.asList("routing-key-1", "routing-key-2"));
        m.put("random", UUID.randomUUID().toString());

        return m;
    }

    private Map<String, Object> argumentsForDeadLetteringTo(String dlx) {
        return argumentsForDeadLetteringTo(dlx, 1);
    }

    private Map<String, Object> argumentsForDeadLetteringWithoutTtlTo(String dlx) {
        return argumentsForDeadLetteringTo(dlx, -1);
    }

    private Map<String, Object> argumentsForDeadLetteringTo(String dlx, int ttl) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("x-dead-letter-exchange", dlx);
        m.put("x-dead-letter-routing-key", "some-routing-key");
        if(ttl > 0) {
            m.put("x-message-ttl", ttl);
        }
        return m;
    }
}
