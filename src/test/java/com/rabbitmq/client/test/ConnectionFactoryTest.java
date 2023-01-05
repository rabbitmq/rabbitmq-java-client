// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class ConnectionFactoryTest {

    // see https://github.com/rabbitmq/rabbitmq-java-client/issues/262
    @Test
    public void tryNextAddressIfTimeoutExceptionNoAutoRecovery() throws IOException, TimeoutException {
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
                new Address[]{new Address("host1"), new Address("host2")}
        );
        assertThat(returnedConnection).isSameAs(connectionThatSucceeds);
    }

    // see https://github.com/rabbitmq/rabbitmq-java-client/pull/350
    @Test
    public void customizeCredentialsProvider() throws Exception {
        final CredentialsProvider provider = mock(CredentialsProvider.class);
        final AMQConnection connection = mock(AMQConnection.class);
        final AtomicBoolean createCalled = new AtomicBoolean(false);

        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler,
                                                     MetricsCollector metricsCollector) {
                assertThat(provider).isSameAs(params.getCredentialsProvider());
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
        assertThat(returnedConnection).isSameAs(connection);
        assertThat(createCalled).isTrue();
    }

    @Test
    public void shouldUseDnsResolutionWhenOneAddressAndNoTls() throws Exception {
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
        assertThat(addressResolver.get()).isNotNull().isInstanceOf(DnsRecordIpAddressResolver.class);
    }

    @Test
    public void shouldUseDnsResolutionWhenOneAddressAndTls() throws Exception {
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

        assertThat(addressResolver.get()).isNotNull().isInstanceOf(DnsRecordIpAddressResolver.class);
    }

    @Test
    public void heartbeatAndChannelMaxMustBeUnsignedShorts() {
        class TestConfig {
            int value;
            Consumer<Integer> call;
            boolean expectException;

            public TestConfig(int value, Consumer<Integer> call, boolean expectException) {
                this.value = value;
                this.call = call;
                this.expectException = expectException;
            }
        }

        ConnectionFactory cf = new ConnectionFactory();
        Consumer<Integer> setHeartbeat = cf::setRequestedHeartbeat;
        Consumer<Integer> setChannelMax = cf::setRequestedChannelMax;

        Stream.of(
                new TestConfig(0, setHeartbeat, false),
                new TestConfig(10, setHeartbeat, false),
                new TestConfig(65535, setHeartbeat, false),
                new TestConfig(-1, setHeartbeat, true),
                new TestConfig(65536, setHeartbeat, true))
                .flatMap(config -> Stream.of(config, new TestConfig(config.value, setChannelMax, config.expectException)))
                .forEach(config -> {
                    if (config.expectException) {
                        assertThatThrownBy(() -> config.call.accept(config.value)).isInstanceOf(IllegalArgumentException.class);
                    } else {
                        config.call.accept(config.value);
                    }
                });

    }

}
