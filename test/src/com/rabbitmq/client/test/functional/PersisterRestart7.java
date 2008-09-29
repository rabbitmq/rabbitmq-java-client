package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;

/**
 * Tests whether durable bindings are correctly recovered.
 */
public class PersisterRestart7 extends PersisterRestartBase {

    private static final int N = 1;

    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();

    public void testRestart() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);
        basicPublishPersistent(X,K);
        assertDelivered(Q,1);

        deleteExchange(X);

        forceSnapshot();
        restart();

        declareDurableTopicExchange(X);

        for (int i = 0; i < N; i++){
            basicPublishPersistent(X,K);
        }

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNull("The initial response SHOULD BE null", response);

        deleteQueue(Q);
    }
}