package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * Test that unbinding from an auto-delete exchange causes the exchange to go
 * away
 */
public class UnbindAutoDeleteExchange extends BrokerTestCase {
    public void testUnbind() throws IOException, InterruptedException {
        String exchange = "myexchange";
        channel.exchangeDeclare(exchange, "fanout", false, true, null);
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, exchange, "");
        channel.queueUnbind(queue, exchange, "");

        try {
            channel.exchangeDeclarePassive(exchange);
            fail("exchange should no longer be there");
        }
        catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
        }
    }
}
