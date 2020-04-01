// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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


package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.functional.ClusteredTestBase;

/**
 * This tests whether 'absent' queues - durable queues whose home node
 * is down - are handled properly.
 */
public class AbsentQueue extends ClusteredTestBase {

    private static final String Q = "absent-queue";

    @Override public void setUp() throws IOException, TimeoutException {
        super.setUp();
        if (clusteredConnection != null)
            stopSecondary();
    }

    @Override public void tearDown() throws IOException, TimeoutException {
        if (clusteredConnection != null)
            startSecondary();
        super.tearDown();
    }

    @Override protected void createResources() throws IOException {
        alternateChannel.queueDeclare(Q, true, false, false, null);
    }

    @Override protected void releaseResources() throws IOException {
        alternateChannel.queueDelete(Q);
    }

    @Test public void notFound() throws IOException, InterruptedException {
        if (!HATests.HA_TESTS_RUNNING) {
            // we don't care about this test in normal mode
            return;
        }
        waitPropagationInHa();
        assertNotFound(new Task() {
                public void run() throws IOException {
                    channel.queueDeclare(Q, true, false, false, null);
                }
            });
        assertNotFound(new Task() {
                public void run() throws IOException {
                    channel.queueDeclarePassive(Q);
                }
            });
        assertNotFound(new Task() {
                public void run() throws IOException {
                    channel.queuePurge(Q);
                }
            });
        assertNotFound(new Task() {
                public void run() throws IOException {
                    channel.basicGet(Q, true);
                }
            });
        assertNotFound(new Task() {
                public void run() throws IOException {
                    channel.queueBind(Q, "amq.fanout", "", null);
                }
            });
    }

    protected void assertNotFound(Task t) throws IOException {
        if (clusteredChannel == null) return;
        try {
            t.run();
            if (!HATests.HA_TESTS_RUNNING) fail("expected not_found");
        } catch (IOException ioe) {
            assertFalse(HATests.HA_TESTS_RUNNING);
            checkShutdownSignal(AMQP.NOT_FOUND, ioe);
            channel = connection.createChannel();
        }

    }

    private void waitPropagationInHa() throws IOException, InterruptedException {
        // can be necessary to wait a bit in HA mode
        if (HATests.HA_TESTS_RUNNING) {
            long waited = 0;
            while(waited < 5000) {
                Channel tempChannel = connection.createChannel();
                try {
                    tempChannel.queueDeclarePassive(Q);
                    break;
                } catch (IOException e) {

                }
                Thread.sleep(10);
                waited += 10;
            }
        }
    }

    private interface Task {
        void run() throws IOException;
    }

}
