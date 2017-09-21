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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.Test;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;

public class PersistenceGuarantees extends BrokerTestCase {
    private static final int COUNT = 10000;
    private String queue;

    @Override
    protected NioParams nioParams() {
        NioParams nioParams = super.nioParams();
        // may need a higher enqueuing timeout on slow environments
        return nioParams
            .setWriteEnqueuingTimeoutInMs(nioParams.getWriteEnqueuingTimeoutInMs() * 3)
            .setWriteQueueCapacity(nioParams.getWriteQueueCapacity() * 2);
    }

    protected void declareQueue() throws IOException {
        queue = channel.queueDeclare("", true, false, false, null).getQueue();
    }

    @Test public void txPersistence() throws Exception {
        declareQueue();
        channel.txSelect();
        publish();
        channel.txCommit();
        restart();
        assertPersisted();
    }

    @Test public void confirmPersistence() throws Exception {
        declareQueue();
        channel.confirmSelect();
        publish();
        channel.waitForConfirms();
        restart();
        assertPersisted();
    }

    private void assertPersisted() throws IOException {
        assertEquals(COUNT, channel.queueDelete(queue).getMessageCount());
    }

    private void publish() throws IOException {
        for (int i = 0; i < COUNT; i++) {
            channel.basicPublish("", queue, false, false, MessageProperties.PERSISTENT_BASIC, "".getBytes());
        }
    }
}
