package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class ExchangeDeletePredeclared extends BrokerTestCase {
    public void testDeletingPredeclaredAmqExchange() throws IOException {
        try {
            channel.exchangeDelete("amq.fanout");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
        }
    }

    public void testDeletingPredeclaredAmqRabbitMQExchange() throws IOException {
        try {
            channel.exchangeDelete("amq.rabbitmq.log");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
        }
    }
}
