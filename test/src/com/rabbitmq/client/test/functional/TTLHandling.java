package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class TTLHandling extends BrokerTestCase {

    protected static final String TTL_EXCHANGE           = "ttl.exchange";
    protected static final String TTL_QUEUE_NAME         = "queue.ttl";
    protected static final String TTL_INVALID_QUEUE_NAME   = "invalid.queue.ttl";
    private static final String TTL_ARG                  = "x-message-ttl";

    protected static final String[] MSG = {"one", "two", "three"};

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(TTL_EXCHANGE, "direct");
    }

    @Override
    protected void releaseResources() throws IOException {
        this.channel.exchangeDelete(TTL_EXCHANGE);
    }

    protected void expectBodyAndRemainingMessages(String body, int messagesLeft) throws IOException {
        GetResponse response = channel.basicGet(TTL_QUEUE_NAME, false);
        assertEquals(body, new String(response.getBody()));
        assertEquals(messagesLeft,  response.getMessageCount());
    }

    protected void declareAndBindQueue(Object ttlValue) throws IOException {
        declareQueue(ttlValue);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    protected AMQP.Queue.DeclareOk declareQueue(Object ttlValue) throws IOException {
        return declareQueue(TTL_QUEUE_NAME, ttlValue);
    }

    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        Map<String, Object> argMap = Collections.singletonMap(TTL_ARG, ttlValue);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    protected String get() throws IOException {
        GetResponse response = basicGet(TTL_QUEUE_NAME);
        return response == null ? null : new String(response.getBody());
    }

    protected void publish(String msg) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME);
    }
}
