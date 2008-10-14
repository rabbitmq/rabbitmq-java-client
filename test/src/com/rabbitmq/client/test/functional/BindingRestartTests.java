package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import junit.framework.TestSuite;


/**
 * These test cases verify the durability of bindings accross
 * broker restarts in various scenarios.
 */
public class BindingRestartTests extends PersisterRestartBase {

    public static TestSuite suite() {
        TestSuite suite = new TestSuite("binding-restarts");
        suite.addTestSuite(BindingRestartTests.class);
        return suite;
    }

    private static final int N = 1;

    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();

    /**
     *   Tests whether durable bindings are correctly recovered.
     */
    public void testDurableBindingRecovery() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);

        restart();

        for (int i = 0; i < N; i++){
            basicPublishVolatile(X, K);
        }

        assertDelivered(Q, N);

        deleteQueue(Q);
        deleteExchange(X);
    }

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
    public void testDurableBindingsDeletion() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);

        deleteExchange(X);

        restart();

        declareDurableTopicExchange(X);

        for (int i = 0; i < N; i++){
            basicPublishVolatile(X, K);
        }

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNull("The initial response SHOULD BE null", response);

        deleteQueue(Q);
        deleteExchange(X);
    }


    /**
     * This tests whether the default bindings for persistent queues are recovered properly.
     *
     * The idea is to create a durable queue, nuke the server and then publish a
     * message to it using the queue name as a routing key
     */
    public void testDefaultBindingRecovery() throws Exception {
        declareDurableQueue(Q);

        restart();

        basicPublishVolatile("", Q);

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNotNull("The initial response SHOULD NOT be null", response);

        deleteQueue(Q);
    }
}
