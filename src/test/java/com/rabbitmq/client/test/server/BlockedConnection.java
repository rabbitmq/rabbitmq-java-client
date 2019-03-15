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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;

public class BlockedConnection extends BrokerTestCase {
    protected void releaseResources() throws IOException {
        try {
            unblock();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    // this test first opens a connection, then triggers
    // and alarm and blocks
    @Test public void testBlock() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Connection connection = connection(latch);
        block();
        publish(connection);

        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            TestUtils.abort(connection);
        }

    }

    // this test first triggers an alarm, then opens a
    // connection
    @Test public void initialBlock() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        block();
        Connection connection = connection(latch);
        publish(connection);

        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            TestUtils.abort(connection);
        }
    }

    private Connection connection(final CountDownLatch latch) throws IOException, TimeoutException {
        ConnectionFactory factory = TestUtils.connectionFactory();
        Connection connection = factory.newConnection();
        connection.addBlockedListener(new BlockedListener() {
            public void handleBlocked(String reason) throws IOException {
                try {
                    unblock();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            public void handleUnblocked() throws IOException {
                latch.countDown();
            }
        });
        return connection;
    }

    private void publish(Connection connection) throws IOException {
        Channel ch = connection.createChannel();
        ch.basicPublish("", "", MessageProperties.BASIC, "".getBytes());
    }
}
