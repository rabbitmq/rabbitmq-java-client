package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * See bug 21843. It's not obvious this is the right thing to do, but it's in
 * the spec.
 */
public class DefaultExchange extends BrokerTestCase {
    String queueName;

    @Override
    protected void createResources() throws IOException {
        queueName = channel.queueDeclare().getQueue();
    }

    // See bug 22101: publish is the only operation permitted on the
    // default exchange

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

    public void testConfigureDefaultExchange() throws IOException {
        channel.exchangeDeclare("", "direct", true);
        channel.exchangeDeclare("amq.default", "direct", true);
    }
}
