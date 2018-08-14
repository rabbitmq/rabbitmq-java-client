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

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.rabbitmq.client.test.TestUtils.closeAllConnectionsAndWaitForRecovery;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NioDeadlockOnConnectionClosing {

    static final Logger LOGGER = LoggerFactory.getLogger(NioDeadlockOnConnectionClosing.class);

    ExecutorService nioExecutorService, connectionShutdownExecutorService;
    ConnectionFactory cf;
    List<Connection> connections;

    @Before
    public void setUp() {
        nioExecutorService = Executors.newFixedThreadPool(2);
        connectionShutdownExecutorService = Executors.newFixedThreadPool(2);
        cf = TestUtils.connectionFactory();
        cf.setAutomaticRecoveryEnabled(true);
        cf.useNio();
        cf.setNetworkRecoveryInterval(1000);
        NioParams params = new NioParams()
            .setNioExecutor(nioExecutorService)
            .setConnectionShutdownExecutor(connectionShutdownExecutorService)
            .setNbIoThreads(2);
        cf.setNioParams(params);
        connections = new ArrayList<>();
    }

    @After
    public void tearDown() throws Exception {
        for (Connection connection : connections) {
            try {
                connection.close(2000);
            } catch (Exception e) {
                LOGGER.warn("Error while closing test connection", e);
            }
        }

        shutdownExecutorService(nioExecutorService);
        shutdownExecutorService(connectionShutdownExecutorService);
    }

    private void shutdownExecutorService(ExecutorService executorService) throws InterruptedException {
        if (executorService == null) {
            return;
        }
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            LOGGER.warn("Couldn't terminate executor after 5 seconds");
        }
    }

    @Test
    public void connectionClosing() throws Exception {
        for (int i = 0; i < 10; i++) {
            connections.add(cf.newConnection());
        }
        closeAllConnectionsAndWaitForRecovery(connections);
        for (Connection connection : connections) {
            assertTrue(connection.isOpen());
        }
    }
}
