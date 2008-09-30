package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;

/**
 *
 * This tests whether the bindings attached to a durable exchange
 * are correctly blown away when the exhange is nuked.
 *
 * This complements a unit test for testing non-durable exhanges.
 * In that case, an exchange is deleted and you expect any bindings
 * hanging to it to be deleted as well. To verify this, the exchange
 * is deleted and then recreated.
 *
 * After the recreation, the old bindings should no longer exist and
 * hence any messages published to that exchange get routed to /dev/null
 *
 * This test exercises the durable variable of that test, so the main
 * difference is that the broker has to be restarted to verify that
 * the durable routes have been turfed.
 *
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