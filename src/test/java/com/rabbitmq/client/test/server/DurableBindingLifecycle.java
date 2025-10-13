// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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


package com.rabbitmq.client.test.server;

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.functional.BindingLifecycleBase;

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
    protected void restart() throws IOException, TimeoutException {
        if (clusteredConnection != null) {
            clusteredConnection.abort(10_000);
            clusteredConnection = null;
            clusteredChannel = null;
            alternateConnection = null;
            alternateChannel = null;

            stopSecondary();
            startSecondary();
        }
        restartPrimary();
    }

    private void restartPrimary() throws IOException, TimeoutException {
        tearDown(this.testInfo);
        bareRestart();
        setUp(this.testInfo);
    }

    /**
     *   Tests whether durable bindings are correctly recovered.
     */
    @Test public void durableBindingRecovery() throws IOException, TimeoutException {
        declareDurableTopicExchange(x);
        declareAndBindDurableQueue(q, x, k);

        restart();

        for (int i = 0; i < N; i++) {
            basicPublishVolatile(x, k);
        }

        AtomicInteger receivedCount = new AtomicInteger(0);
        waitAtMost(() -> {
            GetResponse r;
            r = basicGet(q);
            if (r != null) {
                assertThat(r.getEnvelope().isRedeliver()).isFalse();
                receivedCount.incrementAndGet();
            }
            return receivedCount.get() == N;
        });

        deleteQueue(q);
        deleteExchange(x);
    }

    /**
     * This tests whether the bindings attached to a durable exchange
     * are correctly blown away when the exchange is nuked.
     *
     * This complements a unit test for testing non-durable exchanges.
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
    @Test public void durableBindingsDeletion() throws IOException, TimeoutException {
        declareDurableTopicExchange(x);
        declareAndBindDurableQueue(q, x, k);

        deleteExchange(x);

        restart();

        declareDurableTopicExchange(x);

        for (int i = 0; i < N; i++) {
            basicPublishVolatile(x, k);
        }

        GetResponse response = channel.basicGet(q, true);
        assertNull(response, "The initial response SHOULD BE null");

        deleteQueue(q);
        deleteExchange(x);
    }


    /**
     * This tests whether the default bindings for durable queues
     * are recovered properly.
     *
     * The idea is to create a durable queue, nuke the server and then
     * publish a message to it using the queue name as a routing key
     */
    @Test public void defaultBindingRecovery() throws IOException, TimeoutException {
        declareDurableQueue(q);

        restart();

        basicPublishVolatile("", q);

        waitAtMost(() -> channel.basicGet(q, true) != null);

        deleteQueue(q);
    }

    /**
     * Test that when we have a transient exchange bound to a durable
     * queue and the durable queue is on a cluster node that restarts,
     * we do not lose the binding.  See bug 24009.
     */
    @Test public void transientExchangeDurableQueue() throws IOException, TimeoutException {
        // This test depends on the second node in the cluster to keep
        // the transient X alive
        if (clusteredConnection != null) {
            channel.exchangeDeclare("transientX", "fanout", false);
            channel.queueDeclare("durableQ", true, false, false, null);
            channel.queueBind("durableQ", "transientX", "");

            restartPrimary();

            basicPublishVolatile("transientX", "");
            waitAtMost(() -> {
                GetResponse response = channel.basicGet("durableQ", true);
                if (response == null) {
                    return false;
                } else {
                    assertThat(response.getEnvelope().isRedeliver()).isFalse();
                    return true;
                }
            });

            deleteExchange("transientX");
            deleteQueue("durableQ");
        }
    }
}
