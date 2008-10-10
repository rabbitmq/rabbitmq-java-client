package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;

/**
 * This tests whether the default bindings for persistent queues are recovered properly.
 *
 * The idea is to create a durable queue, nuke the server and then publish a
 * message to it using the queue name as a routing key
 */
public class PersisterRestart8 extends PersisterRestartBase {

    protected static final int N = 1;
    protected static final String Q = "Q-" + System.currentTimeMillis();

    public void testRestart() throws Exception {
        declareDurableQueue(Q);

        forceSnapshot();
        restart();

        basicPublishVolatile("", Q);

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNotNull("The initial response SHOULD NOT be null", response);

        deleteQueue(Q);
    }
}
