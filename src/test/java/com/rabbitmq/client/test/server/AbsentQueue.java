// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.functional.ClusteredTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This tests whether 'absent' queues - durable queues whose home node
 * is down - are handled properly.
 */
public class AbsentQueue extends ClusteredTestBase {

    private static final String Q = "absent-queue";

    @BeforeEach
    @Override public void setUp(TestInfo info) throws IOException, TimeoutException {
        super.setUp(info);
        if (clusteredConnection != null)
            stopSecondary();
    }

    @AfterEach
    @Override public void tearDown(TestInfo info) throws IOException, TimeoutException {
        if (clusteredConnection != null)
            startSecondary();
        super.tearDown(info);
    }

    @Override protected void createResources() throws IOException {
        alternateChannel.queueDeclare(Q, true, false, false, null);
    }

    @Override protected void releaseResources() throws IOException {
        alternateChannel.queueDelete(Q);
    }

    @Test public void notFound() throws Exception {
        if (!ha()) {
            // we don't care about this test in normal mode
            return;
        }
        waitPropagationInHa();
        assertNotFound(() -> channel.queueDeclare(Q, true, false, false, null));
        assertNotFound(() -> channel.queueDeclarePassive(Q));
        assertNotFound(() -> channel.queuePurge(Q));
        assertNotFound(() -> channel.basicGet(Q, true));
        assertNotFound(() -> channel.queueBind(Q, "amq.fanout", "", null));
    }

    protected void assertNotFound(Callable<?> t) throws Exception {
        if (clusteredChannel == null) return;
        try {
            t.call();
            if (!ha()) fail("expected not_found");
        } catch (IOException ioe) {
            assertFalse(ha());
            checkShutdownSignal(AMQP.NOT_FOUND, ioe);
            channel = connection.createChannel();
        }

    }

    private void waitPropagationInHa() throws IOException, InterruptedException {
        // can be necessary to wait a bit in HA mode
        if (ha()) {
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

}
