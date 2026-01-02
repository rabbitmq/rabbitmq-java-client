// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.rabbitmq.client.test.TestUtils.IO_NETTY;
import static com.rabbitmq.client.test.TestUtils.IO_SOCKET;
import static com.rabbitmq.client.test.TestUtils.closeAllConnectionsAndWaitForRecovery;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class IoDeadlockOnConnectionClosing {

    static final Logger LOGGER = LoggerFactory.getLogger(IoDeadlockOnConnectionClosing.class);

    EventLoopGroup eventLoopGroup;
    ConnectionFactory cf;
    List<Connection> connections;

    @ParameterizedTest
    @ValueSource(strings = {IO_NETTY, IO_SOCKET})
    public void connectionClosing(String io) throws Exception {
        init(io);
        try {
            for (int i = 0; i < 10; i++) {
                connections.add(cf.newConnection());
            }
            closeAllConnectionsAndWaitForRecovery(connections);
            for (Connection connection : connections) {
                assertTrue(connection.isOpen());
            }
        } finally {
           tearDown(io);
        }
    }

    private void init(String io) {
        connections = new ArrayList<>();
        cf = TestUtils.connectionFactory();
        if (IO_NETTY.equals(io)) {
            IoHandlerFactory ioHandlerFactory = NioIoHandler.newFactory();
            this.eventLoopGroup = new MultiThreadIoEventLoopGroup(2, ioHandlerFactory);
            cf.netty().eventLoopGroup(eventLoopGroup);
        } else if (IO_SOCKET.equals(io)) {
            cf.useBlockingIo();
        } else {
            throw new IllegalArgumentException("Unknow IO layer: " + io);
        }
    }

    private void tearDown(String io) {
        for (Connection connection : connections) {
            try {
                connection.close(2000);
            } catch (Exception e) {
                LOGGER.warn("Error while closing test connection", e);
            }
        }
        if (IO_NETTY.equals(io)) {
           this.eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }

    }

}
