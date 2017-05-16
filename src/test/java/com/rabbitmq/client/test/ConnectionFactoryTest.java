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

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

public class ConnectionFactoryTest {

    // see https://github.com/rabbitmq/rabbitmq-java-client/issues/262
    @Test public void tryNextAddressIfTimeoutExceptionNoAutoRecovery() throws IOException, TimeoutException {
        final AMQConnection connectionThatThrowsTimeout = mock(AMQConnection.class);
        final AMQConnection connectionThatSucceeds = mock(AMQConnection.class);
        final Queue<AMQConnection> connections = new ArrayBlockingQueue<AMQConnection>(10);
        connections.add(connectionThatThrowsTimeout);
        connections.add(connectionThatSucceeds);
        ConnectionFactory connectionFactory = new ConnectionFactory() {

            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler, MetricsCollector metricsCollector) {
                return connections.poll();
            }

            @Override
            protected synchronized FrameHandlerFactory createFrameHandlerFactory() throws IOException {
                return mock(FrameHandlerFactory.class);
            }
        };
        connectionFactory.setAutomaticRecoveryEnabled(false);
        doThrow(TimeoutException.class).when(connectionThatThrowsTimeout).start();
        doNothing().when(connectionThatSucceeds).start();
        Connection returnedConnection = connectionFactory.newConnection(
            new Address[] { new Address("host1"), new Address("host2") }
        );
        assertSame(connectionThatSucceeds, returnedConnection);
    }

}
