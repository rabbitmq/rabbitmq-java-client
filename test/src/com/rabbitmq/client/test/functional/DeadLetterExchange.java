package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class DeadLetterExchange extends BrokerTestCase {

    private static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    private static final String DEAD_LETTER_EXCHANGE_ARG = "x-dead-letter-exchange";

    private static final String TEST_QUEUE_NAME = "test.queue.dead.letter";

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(DEAD_LETTER_EXCHANGE, "direct");
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

    public void testDeclareQueueWithNoExistentDeadLetterExchange() throws IOException {
        try {
            declareQueue("some.random.exchange.name");
            fail("x-dead-letter-exchange must be a existing exchange name");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }

    }
    private void declareQueue(Object deadLetterExchange) throws IOException {
        Map<String, Object> args = Collections.singletonMap(DEAD_LETTER_EXCHANGE_ARG, deadLetterExchange);
        this.channel.queueDeclare(TEST_QUEUE_NAME, false, true, false, args);
    }


}
