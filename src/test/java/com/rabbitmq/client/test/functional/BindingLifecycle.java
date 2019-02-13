// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.


package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

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
    @Test public void queuePurge() throws IOException {

        Binding binding = setupExchangeBindings(false);
        channel.basicPublish(binding.x, binding.k, null, payload);

        // Purge the queue, and test that we don't receive a message
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
    @Test public void unackedPurge() throws IOException {
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

        channel.basicRecover();
        response = channel.basicGet(binding.q, false);
        channel.basicRecover();
        assertTrue(response.getEnvelope().isRedeliver());
        assertNotNull("The response SHOULD NOT BE null", response);

        // If we recover then purge the message should go away
        channel.queuePurge(binding.q);
        response = channel.basicGet(binding.q, true);
        assertNull("The response SHOULD BE null", response);

        deleteExchangeAndQueue(binding);
    }

    /**
     * This tests whether when you delete an exchange, that any
     * bindings attached to it are deleted as well.
     */
    @Test public void exchangeDelete() throws IOException {

        boolean durable = true;
        Binding binding = setupExchangeAndRouteMessage(true);

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
    @Test public void exchangeIfUnused() throws IOException {

        boolean durable = true;
        Binding binding = setupExchangeBindings(true);

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
     * Then re-declare the queue again and try to rebind it to the same exchange.
     *
     * Because the exchange has been auto-deleted, the bind operation
     * should fail.
     */
    @Test public void exchangeAutoDelete() throws IOException, TimeoutException {
        doAutoDelete(false, 1);
    }

    /**
     * Runs something similar to testExchangeAutoDelete, but adds
     * different queues with the same binding to the same exchange.
     *
     * The difference should be that the original exchange should not
     * get auto-deleted
     */
    @Test public void exchangeAutoDeleteManyBindings() throws IOException, TimeoutException {
        doAutoDelete(false, 10);
    }

    /**
     *
     */
    @Test public void exchangePassiveDeclare() throws IOException {
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
    @Test public void unbind() throws Exception {
        for (String exchange: new String[]{"amq.fanout", "amq.direct", "amq.topic", "amq.headers"}) {
            testUnbind(exchange);
        }
    }

    public void testUnbind(String exchange) throws Exception {
        Binding b = new Binding(channel.queueDeclare().getQueue(),
                                exchange,
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
            // check we can unbind all sorts of things that don't exist
            channel.queueUnbind(test.q, test.x, test.k);
        }

        // success case

        channel.queueBind(b.q, b.x, b.k);
        sendRoutable(b);
        channel.queueUnbind(b.q, b.x, b.k);
        sendUnroutable(b);
    }

}
