package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.functional.ClusteredTestBase;

import java.io.IOException;

/**
 * Test that when we have a transient exchange bound to a durable queue and the
 * durable queue is on a cluster node that restarts, we do not lose the binding.
 * See bug 24009.
 */
public class TransientExchangeDurableQueue extends ClusteredTestBase {

    public void testTransientExchangeDurableQueue() throws IOException {
        channel.exchangeDeclare("transientX", "fanout", false);
        channel.queueDeclare("durableQ", true, false, false, null);
        channel.queueBind("durableQ", "transientX", "");
        
        restartPrimary();

        basicPublishVolatile("transientX", "");
        assertDelivered("durableQ", 1);
    }
}
