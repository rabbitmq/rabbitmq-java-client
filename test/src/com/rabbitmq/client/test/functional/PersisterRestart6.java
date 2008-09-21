package com.rabbitmq.client.test.functional;

/**
 * Tests whether durable bindings are correctly recovered.
 */
public class PersisterRestart6 extends PersisterRestartBase {

    private static final int N = 1;

    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();

    public void testReststart() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);
        basicPublishPersistent(X,K);
        assertDelivered(Q,1);

        for (int i = 0; i < N; i++){
            basicPublishPersistent(X,K);
        }

        forceSnapshot();
        restart();

        assertDelivered(Q, N);

        deleteQueue(Q);
    }
}
