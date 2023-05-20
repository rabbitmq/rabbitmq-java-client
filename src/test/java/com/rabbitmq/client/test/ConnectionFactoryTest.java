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
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.impl.recovery.RecoveredQueueNameSupplier;
import com.rabbitmq.client.impl.recovery.RetryHandler;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
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

    @Test
    public void shouldBeConfigurableUsingFluentAPI() throws Exception {
        /* GIVEN */
        Map<String, Object> clientProperties = new HashMap<>();
        SaslConfig saslConfig = mock(SaslConfig.class);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        SocketFactory socketFactory = mock(SocketFactory.class);
        SocketConfigurator socketConfigurator = mock(SocketConfigurator.class);
        ExecutorService executorService = mock(ExecutorService.class);
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        ThreadFactory threadFactory = mock(ThreadFactory.class);
        ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        CredentialsRefreshService credentialsRefreshService = mock(CredentialsRefreshService.class);
        RecoveryDelayHandler recoveryDelayHandler = mock(RecoveryDelayHandler.class);
        NioParams nioParams = mock(NioParams.class);
        SslContextFactory sslContextFactory = mock(SslContextFactory.class);
        TopologyRecoveryFilter topologyRecoveryFilter = mock(TopologyRecoveryFilter.class);
        Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition = (ShutdownSignalException) -> true;
        RetryHandler retryHandler = mock(RetryHandler.class);
        RecoveredQueueNameSupplier recoveredQueueNameSupplier = mock(RecoveredQueueNameSupplier.class);

        /* WHEN */
        connectionFactory
                .setHost("rabbitmq")
                .setPort(5672)
                .setUsername("guest")
                .setPassword("guest")
                .setVirtualHost("/")
                .setRequestedChannelMax(1)
                .setRequestedFrameMax(2)
                .setRequestedHeartbeat(3)
                .setConnectionTimeout(4)
                .setHandshakeTimeout(5)
                .setShutdownTimeout(6)
                .setClientProperties(clientProperties)
                .setSaslConfig(saslConfig)
                .setSocketFactory(socketFactory)
                .setSocketConfigurator(socketConfigurator)
                .setSharedExecutor(executorService)
                .setShutdownExecutor(executorService)
                .setHeartbeatExecutor(scheduledExecutorService)
                .setThreadFactory(threadFactory)
                .setExceptionHandler(exceptionHandler)
                .setAutomaticRecoveryEnabled(true)
                .setTopologyRecoveryEnabled(true)
                .setTopologyRecoveryExecutor(executorService)
                .setMetricsCollector(metricsCollector)
                .setCredentialsRefreshService(credentialsRefreshService)
                .setNetworkRecoveryInterval(7)
                .setRecoveryDelayHandler(recoveryDelayHandler)
                .setNioParams(nioParams)
                .useNio()
                .useBlockingIo()
                .setChannelRpcTimeout(8)
                .setSslContextFactory(sslContextFactory)
                .setChannelShouldCheckRpcResponseType(true)
                .setWorkPoolTimeout(9)
                .setTopologyRecoveryFilter(topologyRecoveryFilter)
                .setConnectionRecoveryTriggeringCondition(connectionRecoveryTriggeringCondition)
                .setTopologyRecoveryRetryHandler(retryHandler)
                .setRecoveredQueueNameSupplier(recoveredQueueNameSupplier);

        /* THEN */
        assertThat(connectionFactory.getHost()).isEqualTo("rabbitmq");
        assertThat(connectionFactory.getPort()).isEqualTo(5672);
        assertThat(connectionFactory.getUsername()).isEqualTo("guest");
        assertThat(connectionFactory.getPassword()).isEqualTo("guest");
        assertThat(connectionFactory.getVirtualHost()).isEqualTo("/");
        assertThat(connectionFactory.getRequestedChannelMax()).isEqualTo(1);
        assertThat(connectionFactory.getRequestedFrameMax()).isEqualTo(2);
        assertThat(connectionFactory.getRequestedHeartbeat()).isEqualTo(3);
        assertThat(connectionFactory.getConnectionTimeout()).isEqualTo(4);
        assertThat(connectionFactory.getHandshakeTimeout()).isEqualTo(5);
        assertThat(connectionFactory.getShutdownTimeout()).isEqualTo(6);
        assertThat(connectionFactory.getClientProperties()).isEqualTo(clientProperties);
        assertThat(connectionFactory.getSaslConfig()).isEqualTo(saslConfig);
        assertThat(connectionFactory.getSocketFactory()).isEqualTo(socketFactory);
        assertThat(connectionFactory.getSocketConfigurator()).isEqualTo(socketConfigurator);
        assertThat(connectionFactory.isAutomaticRecoveryEnabled()).isEqualTo(true);
        assertThat(connectionFactory.isTopologyRecoveryEnabled()).isEqualTo(true);
        assertThat(connectionFactory.getMetricsCollector()).isEqualTo(metricsCollector);
        assertThat(connectionFactory.getNetworkRecoveryInterval()).isEqualTo(7);
        assertThat(connectionFactory.getRecoveryDelayHandler()).isEqualTo(recoveryDelayHandler);
        assertThat(connectionFactory.getNioParams()).isEqualTo(nioParams);
        assertThat(connectionFactory.getChannelRpcTimeout()).isEqualTo(8);
        assertThat(connectionFactory.isChannelShouldCheckRpcResponseType()).isEqualTo(true);
        assertThat(connectionFactory.getWorkPoolTimeout()).isEqualTo(9);
        assertThat(connectionFactory.isSSL()).isEqualTo(true);

        /* Now test cross-cutting setters that override properties set by other setters */
        CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
        when(credentialsProvider.getUsername()).thenReturn("admin");
        when(credentialsProvider.getPassword()).thenReturn("admin");
        connectionFactory
                .setCredentialsProvider(credentialsProvider)
                .setUri("amqp://host:5671")
                .useSslProtocol("TLSv1.2");
        assertThat(connectionFactory.getHost()).isEqualTo("host");
        assertThat(connectionFactory.getPort()).isEqualTo(5671);
        assertThat(connectionFactory.getUsername()).isEqualTo("admin");
        assertThat(connectionFactory.getPassword()).isEqualTo("admin");
        assertThat(connectionFactory.isSSL()).isEqualTo(true);
    }

}
