//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * TODO: Adjust this test when Queue.Unbind is implemented in the
 * server
 */
public class BindingLifecycle extends PersisterRestartBase {

    protected static final byte[] payload =
        (""+ System.currentTimeMillis()).getBytes();

    private static final int N = 1;

    protected static final String Q = "Q-" + System.currentTimeMillis();
    protected static final String X = "X-" + System.currentTimeMillis();
    protected static final String K = "K-" + System.currentTimeMillis();

    /**
     * Create a durable queue on secondary node, if possible, falling
     * back on the primary node if necessary.
     */
    @Override protected void declareDurableQueue(String q)
        throws IOException
    {
        Connection connection;
        try {
            connection = connectionFactory.newConnection("localhost", 5673);
        } catch (IOException e) {
            super.declareDurableQueue(q);
            return;
        }

        Channel channel = connection.createChannel();

        channel.queueDeclare(q, true);

        channel.abort();
        connection.abort();
    }

    /**
     *   Tests whether durable bindings are correctly recovered.
     */
    public void testDurableBindingRecovery() throws IOException {
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
     * This tests whether the bindings attached to a durable exchange
     * are correctly blown away when the exhange is nuked.
     *
     * This complements a unit test for testing non-durable exhanges.
     * In that case, an exchange is deleted and you expect any
     * bindings hanging to it to be deleted as well. To verify this,
     * the exchange is deleted and then recreated.
     *
     * After the recreation, the old bindings should no longer exist
     * and hence any messages published to that exchange get routed to
     * /dev/null
     *
     * This test exercises the durable variable of that test, so the
     * main difference is that the broker has to be restarted to
     * verify that the durable routes have been turfed.
     */
    public void testDurableBindingsDeletion() throws IOException {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);

        deleteExchange(X);

        restart();

        declareDurableTopicExchange(X);

        for (int i = 0; i < N; i++){
            basicPublishVolatile(X, K);
        }

        GetResponse response = channel.basicGet(Q, true);
        assertNull("The initial response SHOULD BE null", response);

        deleteQueue(Q);
        deleteExchange(X);
    }


    /**
     * This tests whether the default bindings for durable queues
     * are recovered properly.
     *
     * The idea is to create a durable queue, nuke the server and then
     * publish a message to it using the queue name as a routing key
     */
    public void testDefaultBindingRecovery() throws IOException {
        declareDurableQueue(Q);

        restart();

        basicPublishVolatile("", Q);

        GetResponse response = channel.basicGet(Q, true);
        assertNotNull("The initial response SHOULD NOT be null", response);

        deleteQueue(Q);
    }

    /**
     * This tests whether when you delete a queue, that its bindings
     * are deleted as well.
     */
    public void testQueueDelete() throws IOException {

        boolean durable = true;
        Binding binding = setupExchangeAndRouteMessage(durable);

        // Nuke the queue and repeat this test, this time you expect
        // nothing to get routed.
        //
        // TODO: When unbind is implemented, use that instead of
        // deleting and re-creating the queue
        channel.queueDelete(binding.q);
        channel.queueDeclare(binding.q, durable);

        sendUnroutable(binding);

        deleteExchangeAndQueue(binding);
    }

    /**
     * This tests whether when you delete an exchange, that any
     * bindings attached to it are deleted as well.
     */
    public void testExchangeDelete() throws IOException {

        boolean durable = true;
        Binding binding = setupExchangeAndRouteMessage(durable);

        // Nuke the exchange and repeat this test, this time you
        // expect nothing to get routed

        channel.exchangeDelete(binding.x);
        channel.exchangeDeclare(binding.x, "direct");

        sendUnroutable(binding);

        channel.queueDelete(binding.q);
    }

    /**
     * This tests whether the server checks that an exchange is
     * actually being used when you try to delete it with the ifunused
     * flag.
     *
     * To test this, you try to delete an exchange with a queue still
     * bound to it and expect the delete operation to fail.
     */
    public void testExchangeIfUnused() throws IOException {

        boolean durable = true;
        Binding binding = setupExchangeBindings(durable);

        try {
            channel.exchangeDelete(binding.x, true);
        }
        catch (Exception e) {
            // do nothing, this is the correct behaviour
            openChannel();
            deleteExchangeAndQueue(binding);
            return;
        }

        fail("Exchange delete should have failed");
    }

    /**
     * This tests whether the server checks that an auto_delete
     * exchange actually deletes the bindings attached to it when it
     * is deleted.
     *
     * To test this, you declare and auto_delete exchange and bind an
     * auto_delete queue to it.
     *
     * Start a consumer on this queue, send a message, let it get
     * consumed and then cancel the consumer
     *
     * The unsubscribe should cause the queue to auto_delete, which in
     * turn should cause the exchange to auto_delete.
     *
     * Then re-declare the queue again and try to rebind it to the same exhange.
     *
     * Because the exchange has been auto-deleted, the bind operation
     * should fail.
     */
    public void testExchangeAutoDelete() throws IOException {
        doAutoDelete(false, 1);
    }

    /**
     * Runs something similar to testExchangeAutoDelete, but adds
     * different queues with the same binding to the same exchange.
     *
     * The difference should be that the original exchange should not
     * get auto-deleted
     */
    public void testExchangeAutoDeleteManyBindings() throws IOException {
        doAutoDelete(false, 10);
    }

    /** 
     * The same thing as testExchangeAutoDelete, but with durable
     * queues.
     *
     * Main difference is restarting the broker to make sure that the
     * durable queues are blasted away.
     */
    public void testExchangeAutoDeleteDurable() throws IOException {
        doAutoDelete(true, 1);
    }

    /**
     * The same thing as testExchangeAutoDeleteManyBindings, but with
     * durable queues.
     */
    public void testExchangeAutoDeleteDurableManyBindings() throws IOException {
        doAutoDelete(true, 10);
    }

    private void doAutoDelete(boolean durable, int queues) throws IOException {

        String[] queueNames = null;

        Binding binding = Binding.randomBinding();

        channel.exchangeDeclare(binding.x, "direct",
                                false, durable, true, null);
        channel.queueDeclare(binding.q,
                             false, durable, false, true, null);
        channel.queueBind(binding.q, binding.x, binding.k);


        if (queues > 1) {
            int j = queues - 1;
            queueNames = new String[j];
            for (int i = 0 ; i < j ; i++) {
                queueNames[i] = randomString();
                channel.queueDeclare(queueNames[i],
                                     false, durable, false, false, null);
                channel.queueBind(queueNames[i],
                                  binding.x, binding.k);
                channel.basicConsume(queueNames[i], true,
                                     new QueueingConsumer(channel));
            }
        }

        subscribeSendUnsubscribe(binding);

        if (durable) {
            restart();
        }
        
        if (queues > 1) {
            for (String s : queueNames) {
                channel.basicConsume(s, true,
                                     new QueueingConsumer(channel));
                Binding tmp = new Binding(binding.x, s, binding.k);
                sendUnroutable(tmp);
            }
        }

        channel.queueDeclare(binding.q,
                             false, durable, true, true, null);

        // if (queues == 1): Because the exchange does not exist, this
        // bind should fail
        try {
            channel.queueBind(binding.q, binding.x, binding.k);
            sendRoutable(binding);
        }
        catch (Exception e) {
            // do nothing, this is the correct behaviour
            channel = null;
            return;
        }
        
        if (queues == 1) {
            deleteExchangeAndQueue(binding);
            fail("Queue bind should have failed");
        }


        // Do some cleanup
        if (queues > 1) {
            for (String q : queueNames) {
                channel.queueDelete(q);
            }
        }

    }

    private void subscribeSendUnsubscribe(Binding binding) throws IOException {
        String tag = channel.basicConsume(binding.q,
                                          new QueueingConsumer(channel));
        sendUnroutable(binding);
        channel.basicCancel(tag);
    }

    private void sendUnroutable(Binding binding) throws IOException {
        channel.basicPublish(binding.x, binding.k, null, payload);
        GetResponse response = channel.basicGet(binding.q, true);
        assertNull("The response SHOULD BE null", response);
    }

    private void sendRoutable(Binding binding) throws IOException {
        channel.basicPublish(binding.x, binding.k, null, payload);
        GetResponse response = channel.basicGet(binding.q, true);
        assertNotNull("The response should not be null", response);
    }

    private static String randomString() {
        return "-" + System.nanoTime();
    }

    private static class Binding {

        String x, q, k;

        static Binding randomBinding() {
            return new Binding(randomString(), randomString(), randomString());
        }

        private Binding(String x, String q, String k) {
            this.x = x;
            this.q = q;
            this.k = k;
        }
    }

    private void createQueueAndBindToExchange(Binding binding, boolean durable)
        throws IOException {

        channel.exchangeDeclare(binding.x, "direct", durable);
        channel.queueDeclare(binding.q, durable);
        channel.queueBind(binding.q, binding.x, binding.k);
    }

    private void deleteExchangeAndQueue(Binding binding)
        throws IOException {

        channel.queueDelete(binding.q);
        channel.exchangeDelete(binding.x);
    }

    private Binding setupExchangeBindings(boolean durable)
        throws IOException {

        Binding binding = Binding.randomBinding();
        createQueueAndBindToExchange(binding, durable);
        return binding;
    }

    private Binding setupExchangeAndRouteMessage(boolean durable)
        throws IOException {

        Binding binding = setupExchangeBindings(durable);
        sendRoutable(binding);
        return binding;
    }

}
