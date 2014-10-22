package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class Nowait extends BrokerTestCase {
    public void testQueueDeclareWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueDeclarePassive(q);
    }

    public void testQueueBindWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueBindNoWait(q, "amq.fanout", "", null);
    }

    public void testExchangeDeclareWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
            channel.exchangeDeclarePassive(x);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testExchangeBindWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
            channel.exchangeBindNoWait(x, "amq.fanout", "", null);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testExchangeUnbindWithNowait() throws IOException {
        String x = generateExchangeName();
        try {
            channel.exchangeDeclare(x, "fanout", false, false, false, null);
            channel.exchangeBind(x, "amq.fanout", "", null);
            channel.exchangeUnbindNoWait(x, "amq.fanout", "", null);
        } finally {
            channel.exchangeDelete(x);
        }
    }

    public void testQueueDeleteWithNowait() throws IOException {
        String q = generateQueueName();
        channel.queueDeclareNoWait(q, false, true, true, null);
        channel.queueDeleteNoWait(q, false, false);
    }

    public void testExchangeDeleteWithNowait() throws IOException {
        String x = generateExchangeName();
        channel.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
        channel.exchangeDeleteNoWait(x, false);
    }
}
