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


package com.rabbitmq.client.test.server;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

/**
 * This tests whether exclusive, durable queues are deleted when appropriate
 * (following the scenarios given in bug 20578).
 */
public class ExclusiveQueueDurability extends BrokerTestCase {

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
    public void testConnectionQueueSameNode() throws Exception {
        channel.queueDeclare("scenario1", true, true, false, null);
        restartPrimaryAbruptly();
        verifyQueueMissing(channel, "scenario1");
    }

    private void restartPrimaryAbruptly() throws IOException {
        connection = null;
        channel = null;
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
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
