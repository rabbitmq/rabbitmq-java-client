package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DeadLetterExchange extends BrokerTestCase {

    private static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    private static final String DEAD_LETTER_EXCHANGE_ARG = "x-dead-letter-exchange";

    private static final String TEST_QUEUE_NAME = "test.queue.dead.letter";

    private static final String DEAD_LETTER_QUEUE = "queue.dle";

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(DEAD_LETTER_EXCHANGE, "direct");
        this.channel.queueDeclare(DEAD_LETTER_QUEUE, false, true, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        this.channel.exchangeDelete(DEAD_LETTER_EXCHANGE);
    }

    public void testDeclareQueueWithNoDeadLetterExchange() throws IOException {
        this.channel.queueDeclare(TEST_QUEUE_NAME, false, true, false, null);
    }

    public void testDeclareQueueWithExistingDeadLetterExchange() throws IOException {
        declareQueue(DEAD_LETTER_EXCHANGE);
    }

    public void testDeclareQueueWithInvalidDeadLetterExchangeArg() throws IOException {
        try {
            declareQueue(133);
            fail("x-dead-letter-exchange must be a valid exchange name");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }

    }

    public void testDeclareQueueWithNonExistentDeadLetterExchange() throws IOException {
        try {
            declareQueue("some.random.exchange.name");
            fail("x-dead-letter-exchange must be a existing exchange name");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testDeadLetterQueueTTLExpiredMessages() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 1000);
        declareQueue(DEAD_LETTER_EXCHANGE, args);

        this.channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        this.channel.queueBind(DEAD_LETTER_QUEUE, DEAD_LETTER_EXCHANGE, "test");

        this.channel.basicPublish("amq.direct", "test", null, "test message".getBytes());

        Thread.sleep(2000);

        GetResponse getResponse = this.channel.basicGet(DEAD_LETTER_QUEUE, true);
        assertNotNull(getResponse);
        assertEquals("test message", new String(getResponse.getBody()));
        System.out.println(getResponse.getProps());
        System.out.println(getResponse.getProps().getHeaders());
        assertEquals("ffo", getResponse.getProps().getHeaders().get("x-death-reason"));
    }

    private void declareQueue(Object deadLetterExchange) throws IOException {
        declareQueue(deadLetterExchange, new HashMap<String, Object>());
    }

    private void declareQueue(Object deadLetterExchange, Map<String, Object> args) throws IOException {
        args.put(DEAD_LETTER_EXCHANGE_ARG, deadLetterExchange);
        this.channel.queueDeclare(TEST_QUEUE_NAME, false, true, false, args);
    }


}
