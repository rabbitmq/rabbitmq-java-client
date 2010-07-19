package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * See bug 21843. It's not obvious this is the right thing to do, but it's in
 * the spec.
 */
public class BindToDefaultExchange extends BrokerTestCase {
    public void testBindToDefaultExchange() throws IOException {
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, "", "foobar");
        basicPublishVolatile("", "foobar"); // Explicit binding
        basicPublishVolatile("", queue); // Implicit binding
        assertDelivered(queue, 2);
    }
}
