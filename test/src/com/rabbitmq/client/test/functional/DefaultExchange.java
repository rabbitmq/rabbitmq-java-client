package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class DefaultExchange extends BrokerTestCase {
    String queueName;

    @Override
    protected void createResources() throws IOException {
        queueName = channel.queueDeclare().getQueue();
    }

    // See bug 22101: publish and declare are the only operations
    // permitted on the default exchange

    public void testDefaultExchangePublish() throws IOException {
        basicPublishVolatile("", queueName); // Implicit binding
        assertDelivered(queueName, 1);
    }

    public void testBindToDefaultExchange() throws IOException {
        try {
            channel.queueBind(queueName, "", "foobar");
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testUnbindFromDefaultExchange() throws IOException {
        try {
            channel.queueUnbind(queueName, "", queueName);
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testDeclareDefaultExchange() throws IOException {
        try {
            channel.exchangeDeclare("", "direct", true);
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testDeleteDefaultExchange() throws IOException {
        try {
            channel.exchangeDelete("");
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }
}
