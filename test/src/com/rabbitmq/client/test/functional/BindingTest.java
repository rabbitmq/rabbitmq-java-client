package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.GetResponse;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * Provides test coverage for the report.
 *
 * TODO: Adjust this test when Queue.Unbind is implemented in the server
 *
 */
public class BindingTest extends BrokerTestCase {
    
    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();
    protected static final byte[] payload = (""+ System.currentTimeMillis()).getBytes();

    // TODO: This setup code is copy and paste - maybe this should wander up to the super class?
    protected void setUp() throws Exception {
        openConnection();
        openChannel();
    }

    protected void tearDown() throws Exception {

        closeChannel();
        closeConnection();
    }

    public void testBindings() throws Exception {
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

        channel.basicPublish(ticket, X, K, MessageProperties.BASIC, payload);

        response = channel.basicGet(ticket, Q, true);
        assertNull("The second response should be null", response);

    }


}
