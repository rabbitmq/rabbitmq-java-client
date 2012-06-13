//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.GetResponse;

import com.rabbitmq.client.test.functional.BindingLifecycleBase;

import com.rabbitmq.tools.Host;

import java.io.IOException;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * The tests attempt to declare durable queues on a secondary node, if
 * present, and that node is restarted as part of the tests while the
 * primary node is still running. That way we exercise any node-down
 * handler code in the server.
 *
 */
public class DurableBindingLifecycle extends BindingLifecycleBase {
    @Override
    protected void restart() throws IOException {
        if (clusteredConnection != null) {
            clusteredConnection.abort();
            clusteredConnection = null;
            clusteredChannel = null;
            alternateConnection = null;
            alternateChannel = null;

            Host.executeCommand("cd ../rabbitmq-test; make restart-secondary-node");
        }
        restartPrimary();
    }

    private void restartPrimary() throws IOException {
        tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
        setUp();
    }

    /**
     * Tests whether durable bindings are correctly recovered.
     * @throws Exception test
     */
    public void testDurableBindingRecovery() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);

        restart();

        for (int i = 0; i < N; i++) {
            basicPublishVolatile(X, K);
        }

        assertDelivered(Q, N);

        deleteQueue(Q);
        deleteExchange(X);
    }

    /**
     * This tests whether the bindings attached to a durable exchange
     * are correctly blown away when the exchange is nuked.
     * <p/>
     * This complements a unit test for testing non-durable exchanges.
     * In that case, an exchange is deleted and you expect any
     * bindings hanging to it to be deleted as well. To verify this,
     * the exchange is deleted and then recreated.
     * <p/>
     * After the recreation, the old bindings should no longer exist
     * and hence any messages published to that exchange get routed to
     * <code>/dev/null</code>.
     * <p/>
     * This test exercises the durable variable of that test, so the
     * main difference is that the broker has to be restarted to
     * verify that the durable routes have been turfed.
     * @throws Exception test
     */
    public void testDurableBindingsDeletion() throws Exception {
        declareDurableTopicExchange(X);
        declareAndBindDurableQueue(Q, X, K);

        deleteExchange(X);

        restart();

        declareDurableTopicExchange(X);

        for (int i = 0; i < N; i++) {
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
     * <p/>
     * The idea is to create a durable queue, nuke the server and then
     * publish a message to it using the queue name as a routing key
     * @throws Exception test
     */
    public void testDefaultBindingRecovery() throws Exception {
        declareDurableQueue(Q);

        restart();

        basicPublishVolatile("", Q);

        GetResponse response = channel.basicGet(Q, true);
        assertNotNull("The initial response SHOULD NOT be null", response);

        deleteQueue(Q);
    }

    /**
     * Test that when we have a transient exchange bound to a durable
     * queue and the durable queue is on a cluster node that restarts,
     * we do not lose the binding.
     * <p/>
     * See <a href="https://bugzilla.rabbitmq.com/show_bug.cgi?id=24009">bug 24009</a>.
     * @throws Exception test
     */
    public void testTransientExchangeDurableQueue() throws Exception {
        // This test depends on the second node in the cluster to keep
        // the transient X alive
        if (clusteredConnection != null) {
            channel.exchangeDeclare("transientX", "fanout", false);
            channel.queueDeclare("durableQ", true, false, false, null);
            channel.queueBind("durableQ", "transientX", "");

            restartPrimary();

            basicPublishVolatile("transientX", "");
            assertDelivered("durableQ", 1);

            deleteExchange("transientX");
            deleteQueue("durableQ");
        }
    }
}
