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

package com.rabbitmq.client.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import org.junit.Test;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.AMQConnection;

public class SharedThreadPoolTest extends BrokerTestCase {
    @Test public void willShutDownExecutor() throws IOException, TimeoutException {
        ExecutorService executor1 = null;
        ExecutorService executor2 = null;
        AMQConnection conn1 = null;
        AMQConnection conn2 = null;
        AMQConnection conn3 = null;
        AMQConnection conn4 = null;
        try {
            ConnectionFactory cf = TestUtils.connectionFactory();
            cf.setAutomaticRecoveryEnabled(false);
            executor1 = Executors.newFixedThreadPool(8);
            cf.setSharedExecutor(executor1);

            conn1 = (AMQConnection)cf.newConnection();
            assertFalse(conn1.willShutDownConsumerExecutor());

            executor2 = Executors.newSingleThreadExecutor();
            conn2 = (AMQConnection)cf.newConnection(executor2);
            assertFalse(conn2.willShutDownConsumerExecutor());

            conn3 = (AMQConnection)cf.newConnection((ExecutorService)null);
            assertTrue(conn3.willShutDownConsumerExecutor());

            cf.setSharedExecutor(null);

            conn4 = (AMQConnection)cf.newConnection();
            assertTrue(conn4.willShutDownConsumerExecutor());
        } finally {
            close(conn1);
            close(conn2);
            close(conn3);
            close(conn4);
            close(executor1);
            close(executor2);
        }
        
    }

    void close(ExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    void close(Connection connection) throws IOException {
        if (connection != null) {
            connection.close();
        }
    }
}
