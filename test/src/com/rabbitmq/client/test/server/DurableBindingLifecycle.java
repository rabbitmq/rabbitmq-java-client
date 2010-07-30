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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
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
        tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
        setUp();
    }

    /**
     *   Tests whether durable bindings are correctly recovered.
     */
    public void testDurableBindingRecovery() throws IOException {
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


}
