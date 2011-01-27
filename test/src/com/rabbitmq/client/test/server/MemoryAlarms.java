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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.server;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

public class MemoryAlarms extends BrokerTestCase {

    private static final String Q = "Restart";

    private Connection connection2;
    private Channel channel2;

    @Override
    protected void setUp() throws IOException {
        connectionFactory.setRequestedHeartbeat(1);
        super.setUp();
        if (connection2 == null) {
            connection2 = connectionFactory.newConnection();
        }
        channel2 = connection2.createChannel();
    }

    @Override
    protected void tearDown() throws IOException {
        if (channel2 != null) {
            channel2.abort();
            channel2 = null;
        }
        if (connection2 != null) {
            connection2.abort();
            connection2 = null;
        }
        super.tearDown();
        connectionFactory.setRequestedHeartbeat(0);
    }

    @Override
    protected void createResources() throws IOException {
        channel.queueDeclare(Q, false, false, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
    }

    protected void setMemoryAlarm() throws IOException, InterruptedException {
        Host.executeCommand("cd ../rabbitmq-test; make set-memory-alarm");
    }

    protected void clearMemoryAlarm() throws IOException, InterruptedException {
        Host.executeCommand("cd ../rabbitmq-test; make clear-memory-alarm");
    }

    public void testFlowControl() throws IOException, InterruptedException {
        basicPublishVolatile(Q);
        setMemoryAlarm();
        // non-publish actions only after an alarm should be fine
        assertNotNull(basicGet(Q));
        QueueingConsumer c = new QueueingConsumer(channel);
        String consumerTag = channel.basicConsume(Q, true, c);
        // publishes after an alarm should not go through
        basicPublishVolatile(Q);
        // the publish is async, so this is racy. This also tests we don't die
        // by heartbeat (3x heartbeat interval + epsilon)
        assertNull(c.nextDelivery(3100));
        // once the alarm has cleared the publishes should go through
        clearMemoryAlarm();
        assertNotNull(c.nextDelivery());
        // everything should be back to normal
        channel.basicCancel(consumerTag);
        basicPublishVolatile(Q);
        assertNotNull(basicGet(Q));
    }

}
