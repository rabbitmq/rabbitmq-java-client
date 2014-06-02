package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class Nowait extends BrokerTestCase {
    public void testQueueDeclareWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNowait(q, false, true, true, null);
        channel.queueDeclarePassive(q);
    }

    public void testQueueBindWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNowait(q, false, true, true, null);
        channel.queueBindNowait(q, "amq.fanout", "", null);
    }

    public void testExchangeDeclareWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNowait(x, "fanout", false, false, false, null);
            channel.exchangeDeclarePassive(x);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testExchangeBindWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNowait(x, "fanout", false, false, false, null);
            channel.exchangeBindNowait(x, "amq.fanout", "", null);
        } finally {
            channel.exchangeDelete(x);
        }
    }

}
