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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.server;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.functional.ClusteredTestBase;

/**
 * This tests whether 'absent' queues - durable queues whose home node
 * is down - are handled properly.
 */
public class AbsentQueue extends ClusteredTestBase {

    private static final String Q = "absent-queue";

    @Override protected void setUp() throws IOException, TimeoutException {
        super.setUp();
        if (clusteredConnection != null)
            stopSecondary();
    }

    @Override protected void tearDown() throws IOException, TimeoutException {
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

    public void testNotFound() throws IOException {
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

    private interface Task {
        public void run() throws IOException;
    }

}
