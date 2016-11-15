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


package com.rabbitmq.client.test.server;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * This tests whether exclusive, durable queues are deleted when appropriate
 * (following the scenarios given in bug 20578).
 */
public class ExclusiveQueueDurability extends BrokerTestCase {

    @Override
    protected boolean isAutomaticRecoveryEnabled() {
        // With automatic recovery enabled, queue can be re-created when launching the test suite
        // (because FunctionalTests are launched independently and as part of the HATests)
        // This then makes this test fail.
        return false;
    }

    void verifyQueueMissing(Channel channel, String queueName)
            throws IOException {
        try {
            channel.queueDeclare(queueName, false, false, false, null);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            fail("Declaring the queue resulted in a channel exception, probably meaning that it already exists");
        }
    }

    // 1) connection and queue are on same node, node restarts -> queue
    // should no longer exist
    @Test public void connectionQueueSameNode() throws Exception {
        channel.queueDeclare("scenario1", true, true, false, null);
        restartPrimaryAbruptly();
        verifyQueueMissing(channel, "scenario1");
    }

    private void restartPrimaryAbruptly() throws IOException, TimeoutException {
        connection = null;
        channel = null;
        bareRestart();
        setUp();
    }

    /*
     * The other scenarios:
     *
     * 2) connection and queue are on different nodes, queue's node restarts,
     * connection is still alive -> queue should exist
     *
     * 3) connection and queue are on different nodes, queue's node restarts,
     * connection has been terminated in the meantime -> queue should no longer
     * exist
     *
     * There's no way to test these, as things stand; connections and queues are
     * tied to nodes, so one can't engineer a situation in which a connection
     * and its exclusive queue are on different nodes.
     */
}
