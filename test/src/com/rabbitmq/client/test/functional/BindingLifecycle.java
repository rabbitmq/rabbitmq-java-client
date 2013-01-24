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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * The tests attempt to declare durable queues on a secondary node, if
 * present, and that node is restarted as part of the tests while the
 * primary node is still running. That way we exercise any node-down
 * handler code in the server.
 *
 */
public class BindingLifecycle extends BindingLifecycleBase {

    /**
     * This tests that when you purge a queue, all of its messages go.
     */
    public void testQueuePurge() throws IOException {

        Binding binding = setupExchangeBindings(false);
        channel.basicPublish(binding.x, binding.k, null, payload);

        // Purge the queue, and test that we don't recieve a message
        channel.queuePurge(binding.q);

        GetResponse response = channel.basicGet(binding.q, true);
        assertNull("The response SHOULD BE null", response);

        deleteExchangeAndQueue(binding);
    }

    /**
     * See bug 21854:
     * "When Queue.Purge is called, sent-but-unacknowledged messages are no
     * longer purged, even if the channel they were sent down is not
     * (Tx-)transacted."
     */
    @SuppressWarnings("deprecation")
    public void testUnackedPurge() throws IOException {
        Binding binding = setupExchangeBindings(false);
        channel.basicPublish(binding.x, binding.k, null, payload);

        GetResponse response = channel.basicGet(binding.q, false);
        assertFalse(response.getEnvelope().isRedeliver());
        assertNotNull("The response SHOULD NOT BE null", response);

        // If we purge the queue the unacked message should still be there on
        // recover.
        channel.queuePurge(binding.q);
        response = channel.basicGet(binding.q, true);
        assertNull("The response SHOULD BE null", response);

        channel.basicRecoverAsync(true);
        response = channel.basicGet(binding.q, false);
        assertTrue(response.getEnvelope().isRedeliver());
        assertNotNull("The response SHOULD NOT BE null", response);

        // If we recover then purge the message should go away
        channel.basicRecoverAsync(true);
        channel.queuePurge(binding.q);
        response = channel.basicGet(binding.q, true);
        assertNull("The response SHOULD BE null", response);

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
        catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
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
     *
     */
    public void testExchangePassiveDeclare() throws IOException {
        channel.exchangeDeclare("testPassive", "direct");
        channel.exchangeDeclarePassive("testPassive");

        try {
            channel.exchangeDeclarePassive("unknown_exchange");
            fail("Passive declare of an unknown exchange should fail");
        }
        catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_FOUND, ioe);
        }
    }

    /**
     * Test the behaviour of queue.unbind
     */
    public void testUnbind() throws Exception {

        Binding b = new Binding(channel.queueDeclare().getQueue(),
                                "amq.direct",
                                "quay");

        // failure cases

        Binding[] tests = new Binding[] {
            new Binding("unknown_queue", b.x, b.k),
            new Binding(b.q, "unknown_exchange", b.k),
            new Binding("unknown_unknown", "exchange_queue", b.k),
            new Binding(b.q, b.x, "unknown_rk"),
            new Binding("unknown_queue", "unknown_exchange", "unknown_rk")
        };

        for (int i = 0; i < tests.length; i++) {

            Binding test = tests[i];
            try {
                channel.queueUnbind(test.q, test.x, test.k);
                fail("expected not_found in test " + i);
            } catch (IOException ee) {
                checkShutdownSignal(AMQP.NOT_FOUND, ee);
                openChannel();
            }
        }

        // success case

        channel.queueBind(b.q, b.x, b.k);
        sendRoutable(b);
        channel.queueUnbind(b.q, b.x, b.k);
        sendUnroutable(b);
    }

}
