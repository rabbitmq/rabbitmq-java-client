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
import com.rabbitmq.client.AddressResolver;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DnsRecordIpAddressResolver;
import com.rabbitmq.client.ListAddressResolver;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
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
            protected synchronized FrameHandlerFactory createFrameHandlerFactory() {
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
    
    // see https://github.com/rabbitmq/rabbitmq-java-client/pull/350
    @Test public void customizeCredentialsProvider() throws Exception {
        final CredentialsProvider provider = mock(CredentialsProvider.class);
        final AMQConnection connection = mock(AMQConnection.class);
        final AtomicBoolean createCalled = new AtomicBoolean(false);
        
        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler,
                    MetricsCollector metricsCollector) {
                assertSame(provider, params.getCredentialsProvider());
                createCalled.set(true);
                return connection;
            }

            @Override
            protected synchronized FrameHandlerFactory createFrameHandlerFactory() {
                return mock(FrameHandlerFactory.class);
            }
        };
        connectionFactory.setCredentialsProvider(provider);
        connectionFactory.setAutomaticRecoveryEnabled(false);
        
        doNothing().when(connection).start();
        
        Connection returnedConnection = connectionFactory.newConnection();
        assertSame(returnedConnection, connection);
        assertTrue(createCalled.get());
    }

    @Test public void shouldNotUseDnsResolutionWhenOneAddressAndNoTls() throws Exception {
        AMQConnection connection = mock(AMQConnection.class);
        AtomicReference<AddressResolver> addressResolver = new AtomicReference<>();

        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler,
                MetricsCollector metricsCollector) {
                return connection;
            }

            @Override
            protected AddressResolver createAddressResolver(List<Address> addresses) {
                addressResolver.set(super.createAddressResolver(addresses));
                return addressResolver.get();
            }

            @Override
            protected synchronized FrameHandlerFactory createFrameHandlerFactory() {
                return mock(FrameHandlerFactory.class);
            }
        };
        // connection recovery makes the creation path more complex
        connectionFactory.setAutomaticRecoveryEnabled(false);

        doNothing().when(connection).start();
        connectionFactory.newConnection();

        assertThat(addressResolver.get(), allOf(notNullValue(), instanceOf(ListAddressResolver.class)));
    }

    @Test public void shouldNotUseDnsResolutionWhenOneAddressAndTls() throws Exception {
        AMQConnection connection = mock(AMQConnection.class);
        AtomicReference<AddressResolver> addressResolver = new AtomicReference<>();

        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler,
                MetricsCollector metricsCollector) {
                return connection;
            }

            @Override
            protected AddressResolver createAddressResolver(List<Address> addresses) {
                addressResolver.set(super.createAddressResolver(addresses));
                return addressResolver.get();
            }

            @Override
            protected synchronized FrameHandlerFactory createFrameHandlerFactory() {
                return mock(FrameHandlerFactory.class);
            }
        };
        // connection recovery makes the creation path more complex
        connectionFactory.setAutomaticRecoveryEnabled(false);

        doNothing().when(connection).start();
        connectionFactory.useSslProtocol();
        connectionFactory.newConnection();

        assertThat(addressResolver.get(), allOf(notNullValue(), instanceOf(ListAddressResolver.class)));
    }

}
