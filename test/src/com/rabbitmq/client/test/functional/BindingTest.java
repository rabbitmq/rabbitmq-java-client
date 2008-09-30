package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.tools.Host;

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

    protected static final byte[] payload = (""+ System.currentTimeMillis()).getBytes();

    private boolean shouldClose;

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

    /**
     * This tests whether when you delete a queue, that it's bindings are deleted as well.
     */
    public void testQueueDelete() throws Exception {

        String x = randomString();
        String q = randomString();
        String k = randomString();

        // create durable exchange and queue and bind them
        channel.exchangeDeclare(ticket, x, "direct", true);
        channel.queueDeclare(ticket, q, true);
        channel.queueBind(ticket, q, x, k);

        // Send it some junk
        channel.basicPublish(ticket, x, k, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, q, true);
        assertNotNull("The initial response should not be null", response);

        // Nuke the queue and repeat this test, this time you expect nothing to get routed
        // TODO: When unbind is implemented, use that instead of deleting and re-creating the queue
        channel.queueDelete(ticket, q);
        channel.queueDeclare(ticket, q, true);

        sendUnroutable(x, k, q);

        channel.queueDelete(ticket, q);
    }

    /**
     * This tests whether when you delete an exchange, that any bindings attached to it are deleted as well.
     */
    public void testExchangeDelete() throws Exception {

        String x = randomString();
        String q = randomString();
        String k = randomString();

        // create durable exchange and queue and bind them
        channel.exchangeDeclare(ticket, x, "direct", true);
        channel.queueDeclare(ticket, q, true);
        channel.queueBind(ticket, q, x, k);

        // Send it some junk
        channel.basicPublish(ticket, x, k, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, q, true);
        assertNotNull("The initial response should not be null", response);

        // Nuke the exchange and repeat this test, this time you expect nothing to get routed

        channel.exchangeDelete(ticket, x);
        channel.exchangeDeclare(ticket, x, "direct");

        sendUnroutable(x, k, q);

        channel.queueDelete(ticket, q);
    }

    /**
     * This tests whether the server checks that an exchange is actually being
     * used when you try to delete it with the ifunused flag.
     * To test this, you try to delete an exchange with a queue still bound to it
     * and expect the delete operation to fail.
     */
    public void testExchangeIfUnused() throws Exception {

        String x = randomString();
        String q = randomString();
        String k = randomString();
        
        channel.exchangeDeclare(ticket, x, "direct", true);
        channel.queueDeclare(ticket, q, true);
        channel.queueBind(ticket, q, x, k);
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

    /**
     * This tests whether the server checks that an auto_delete exchange
     * actually deletes the bindings attached to it when it is deleted.
     *
     * To test this, you declare and auto_delete exchange and bind an auto_delete queue to it.
     *
     * Start a consumer on this queue, send a message, let it get consumed and then cancel the consumer
     *
     * The unsubscribe should cause the queue to auto_delete, which in turn should cause
     * the exchange to auto_delete.
     *
     * Then re-declare the queue again and try to rebind it to the same exhange.
     *
     * Because the exchange has been auto-deleted, the bind operation should fail.
     */
    public void testExchangeAutoDelete() throws Exception {
        doAutoDelete(false);
    }

    /** 
     * The same thing as testExchangeAutoDelete, but with durable queues.
     *
     * Main difference is restarting the broker to make sure that the durable queues are blasted away.
     */
    public void testExchangeAutoDeleteDurable() throws Exception {
        doAutoDelete(true);
    }

    private void doAutoDelete(boolean durable) throws Exception {

        String x = randomString();
        String q = randomString();
        String k = randomString();


        channel.exchangeDeclare(ticket, x, "direct", false, durable, true, null);
        channel.queueDeclare(ticket, q, false, durable, false, true, null);
        channel.queueBind(ticket, q, x, k);

        String tag = channel.basicConsume(ticket, q, new QueueingConsumer(channel));

        sendUnroutable(x, k, q);

        channel.basicCancel(tag);

        if (durable) {
            Host.executeCommand("cd ../rabbitmq-test; make force-snapshot");
            Host.executeCommand("cd ../rabbitmq-test; make restart-on-node");
            connection = connectionFactory.newConnection("localhost");
            openChannel();
        }

        channel.queueDeclare(ticket, q, false, durable, true, true, null);

        // Because the exchange does not exist, this bind should fail
        try {
            channel.queueBind(ticket, q, x, k);
        }
        catch (Exception e) {
            // do nothing, this is the correct behaviour
            shouldClose = false;
            return;
        }

        fail("Queue bind should have failed");
    }

    private void sendUnroutable(String x, String k, String q) throws IOException {
        // Send it some junk
        channel.basicPublish(ticket, x, k, MessageProperties.BASIC, payload);

        GetResponse response = channel.basicGet(ticket, q, true);
        assertNull("The initial response SHOULD BE null", response);
    }

    private String randomString() {
        return "-" + System.nanoTime();
    }

}
