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
        // This test depends on the second node in the cluster to keep the transient X alive
        if (clusteredChannel != null) {
            channel.exchangeDeclare("transientX", "fanout", false);
            declareAndBindDurableQueue("durableQ", "transientX", "");

            restartPrimary();

            basicPublishVolatile("transientX", "");
            assertDelivered("durableQ", 1);

            deleteExchange("transientX");
            deleteQueue("durableQ");
        }
    }
}
