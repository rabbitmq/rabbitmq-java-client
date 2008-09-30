package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * Provides test coverage for the report.
 *
 * TODO: Adjust this test when Queue.Unbind is implemented in the server
 *
 */
public class BindingTest extends BrokerTestCase {

    private boolean shouldClose;

    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();
    protected static final byte[] payload = (""+ System.currentTimeMillis()).getBytes();

    // TODO: This setup code is copy and paste - maybe this should wander up to the super class?
    protected void setUp() throws Exception {
        shouldClose = true;
        openConnection();
        openChannel();
    }

    protected void tearDown() throws Exception {

        if (shouldClose) closeChannel();
        closeConnection();
    }

    public void testQueueDelete() throws Exception {
        // create durable exchange and queue and bind them
        channel.exchangeDeclare(ticket, X, "direct", true);
        channel.queueDeclare(ticket, Q, true);
        channel.queueBind(ticket, Q, X, K);

        // Send it some junk
        channel.basicPublish(ticket, X, K, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNotNull("The initial response should not be null", response);

        // Nuke the queue and repeat this test, this time you expect nothing to get routed
        // TODO: When unbind is implemented, use that instead of deleting and re-creating the queue
        channel.queueDelete(ticket, Q);
        channel.queueDeclare(ticket, Q, true);

        sendUnroutable(X);

        channel.queueDelete(ticket, Q);
    }

    public void testExchangeDelete() throws Exception {
        // create durable exchange and queue and bind them
        channel.exchangeDeclare(ticket, X, "direct", true);
        channel.queueDeclare(ticket, Q, true);
        channel.queueBind(ticket, Q, X, K);

        // Send it some junk
        channel.basicPublish(ticket, X, K, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNotNull("The initial response should not be null", response);

        // Nuke the exchange and repeat this test, this time you expect nothing to get routed

        channel.exchangeDelete(ticket, X);
        channel.exchangeDeclare(ticket, X, "direct");

        sendUnroutable(X);

        channel.queueDelete(ticket, Q);
    }

    public void testExchangeIfUnused() throws Exception {

        // Generate a new exchange name on the stack, not as a member variable
        // otherwise the other tests will interfere.
        String x = "X-" + System.currentTimeMillis();
        
        channel.exchangeDeclare(ticket, x, "direct", true);
        channel.queueDeclare(ticket, Q, true);
        channel.queueBind(ticket, Q, x, K);
        try {
            channel.exchangeDelete(ticket, x, true);
        }
        catch (Exception e) {
            // do nothing, this is the correct behaviour
            shouldClose = false;
            return;
        }

        fail("Exchange delete should have failed");
    }

    public void testExchangeAutoDelete() throws Exception {
        doAutoDelete(false);
        // TODO do we need to test auto_delete on durable exchanges?
    }

    private void doAutoDelete(boolean durable) throws IOException {

        // Generate a new exchange name on the stack, not as a member variable
        // otherwise the other tests will interfere.
        String x = "X-" + System.currentTimeMillis();
        String q = "Q-" + System.currentTimeMillis();


        channel.exchangeDeclare(ticket, x, "direct", false, durable, true, null);
        channel.queueDeclare(ticket, q, false, durable, false, true, null);
        channel.queueBind(ticket, q, x, K);

        String tag = channel.basicConsume(ticket, q, new QueueingConsumer(channel));

        sendUnroutable(x);

        channel.basicCancel(tag);

        channel.queueDeclare(ticket, q, false, false, true, true, null);

        // Because the exchange does not exist, this bind should fail
        try {
            channel.queueBind(ticket, q, x, K);
        }
        catch (Exception e) {
            // do nothing, this is the correct behaviour
            shouldClose = false;
            return;
        }

        fail("Queue bind should have failed");
    }

    private void sendUnroutable(String x) throws IOException {
        // Send it some junk
        channel.basicPublish(ticket, x, K, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, Q, true);
        assertNull("The initial response SHOULD BE null", response);
    }



// The tests do not provide any coverage of exchange auto-deletion or explicit
// exchange deletion with if_unused=true.


}
