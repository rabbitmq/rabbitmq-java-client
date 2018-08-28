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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

public class MemoryAlarms extends BrokerTestCase {

    private static final String Q = "Restart";

    private Connection connection2;
    private Channel channel2;

    @Override
    public void setUp() throws IOException, TimeoutException {
        connectionFactory.setRequestedHeartbeat(1);
        super.setUp();
        if (connection2 == null) {
            connection2 = connectionFactory.newConnection();
        }
        channel2 = connection2.createChannel();
    }

    @Override
    public void tearDown() throws IOException, TimeoutException {
        clearAllResourceAlarms();
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

    @Test public void flowControl() throws IOException, InterruptedException {
        basicPublishVolatile(Q);
        setResourceAlarm("memory");
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
        clearResourceAlarm("memory");
        assertNotNull(c.nextDelivery(3100));
        // everything should be back to normal
        channel.basicCancel(consumerTag);
        basicPublishVolatile(Q);
        assertNotNull(basicGet(Q));
    }


    @Test public void overlappingAlarmsFlowControl() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String consumerTag = channel.basicConsume(Q, true, c);

        setResourceAlarm("memory");
        basicPublishVolatile(Q);

        assertNull(c.nextDelivery(100));
        setResourceAlarm("disk");
        assertNull(c.nextDelivery(100));
        clearResourceAlarm("memory");
        assertNull(c.nextDelivery(100));
        clearResourceAlarm("disk");
        assertNotNull(c.nextDelivery(3100));

        channel.basicCancel(consumerTag);
        basicPublishVolatile(Q);
        assertNotNull(basicGet(Q));
    }


}
