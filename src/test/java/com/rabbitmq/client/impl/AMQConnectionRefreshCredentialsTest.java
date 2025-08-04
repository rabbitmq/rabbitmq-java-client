// Copyright (c) 2019-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.Method;
import com.rabbitmq.client.*;
import com.rabbitmq.client.observation.ObservationCollector;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.TestUtils.BrokerVersion;
import com.rabbitmq.client.test.TestUtils.BrokerVersionAtLeast;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@BrokerVersionAtLeast(BrokerVersion.RABBITMQ_3_8)
public class AMQConnectionRefreshCredentialsTest {

    @Mock
    CredentialsProvider credentialsProvider;

    @Mock
    CredentialsRefreshService refreshService;

    AutoCloseable mocks;

    @BeforeEach
    void init() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static ConnectionFactory connectionFactoryThatSendsGarbageAfterUpdateSecret() {
        ConnectionFactory cf = new ConnectionFactory() {
            @Override
            protected AMQConnection createConnection(ConnectionParams params, FrameHandler frameHandler, MetricsCollector metricsCollector) {
                return new AMQConnection(params, frameHandler, metricsCollector, ObservationCollector.NO_OP) {

                    @Override
                    AMQChannel createChannel0() {
                        return new AMQChannel(this, 0) {
                            @Override
                            public boolean processAsync(Command c) throws IOException {
                                return getConnection().processControlCommand(c);
                            }

                            @Override
                            public AMQCommand rpc(Method m) throws IOException, ShutdownSignalException {
                                if (m instanceof UpdateSecretExtension.UpdateSecret) {
                                    super.rpc(m);
                                    return super.rpc(new UpdateSecretExtension.UpdateSecret(LongStringHelper.asLongString(""), "Refresh scheduled by client") {
                                        @Override
                                        public int protocolMethodId() {
                                            return 255;
                                        }
                                    });
                                } else {
                                    return super.rpc(m);
                                }

                            }
                        };

                    }
                };
            }
        };
        cf.setAutomaticRecoveryEnabled(false);
        TestUtils.setIoLayer(cf);
        return cf;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void connectionIsUnregisteredFromRefreshServiceWhenClosed() throws Exception {
        when(credentialsProvider.getUsername()).thenReturn("guest");
        when(credentialsProvider.getPassword()).thenReturn("guest");
        when(credentialsProvider.getTimeBeforeExpiration()).thenReturn(Duration.ofSeconds(10));

        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setCredentialsProvider(credentialsProvider);

        String registrationId = UUID.randomUUID().toString();
        CountDownLatch unregisteredLatch = new CountDownLatch(1);

        AtomicReference<Callable<Boolean>> refreshTokenCallable = new AtomicReference<>();
        when(refreshService.register(eq(credentialsProvider), any(Callable.class))).thenAnswer(invocation -> {
            refreshTokenCallable.set(invocation.getArgument(1));
            return registrationId;
        });
        doAnswer(invocation -> {
            unregisteredLatch.countDown();
            return null;
        }).when(refreshService).unregister(credentialsProvider, registrationId);

        cf.setCredentialsRefreshService(refreshService);

        verify(refreshService, never()).register(any(CredentialsProvider.class), any(Callable.class));
        try (Connection c = cf.newConnection()) {
            verify(refreshService, times(1)).register(eq(credentialsProvider), any(Callable.class));
            Channel ch = c.createChannel();
            String queue = ch.queueDeclare().getQueue();
            TestUtils.sendAndConsumeMessage("", queue, queue, c);
            verify(refreshService, never()).unregister(any(CredentialsProvider.class), anyString());
            // calling refresh
            assertThat(refreshTokenCallable.get().call()).isTrue();
        }
        verify(refreshService, times(1)).register(eq(credentialsProvider), any(Callable.class));
        assertThat(unregisteredLatch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(refreshService, times(1)).unregister(credentialsProvider, registrationId);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void connectionIsUnregisteredFromRefreshServiceIfUpdateSecretFails() throws Exception {
        when(credentialsProvider.getUsername()).thenReturn("guest");
        when(credentialsProvider.getPassword()).thenReturn("guest");
        when(credentialsProvider.getTimeBeforeExpiration()).thenReturn(Duration.ofSeconds(10));

        ConnectionFactory cf = connectionFactoryThatSendsGarbageAfterUpdateSecret();
        cf.setCredentialsProvider(credentialsProvider);

        String registrationId = UUID.randomUUID().toString();
        CountDownLatch unregisteredLatch = new CountDownLatch(1);
        AtomicReference<Callable<Boolean>> refreshTokenCallable = new AtomicReference<>();
        when(refreshService.register(eq(credentialsProvider), any(Callable.class))).thenAnswer(invocation -> {
            refreshTokenCallable.set(invocation.getArgument(1));
            return registrationId;
        });
        doAnswer(invocation -> {
            unregisteredLatch.countDown();
            return null;
        }).when(refreshService).unregister(credentialsProvider, registrationId);

        cf.setCredentialsRefreshService(refreshService);

        Connection c = cf.newConnection();
        verify(refreshService, times(1)).register(eq(credentialsProvider), any(Callable.class));
        Channel ch = c.createChannel();
        String queue = ch.queueDeclare().getQueue();
        TestUtils.sendAndConsumeMessage("", queue, queue, c);
        verify(refreshService, never()).unregister(any(CredentialsProvider.class), anyString());

        // calling refresh, this sends garbage and should make the broker close the connection
        assertThat(refreshTokenCallable.get().call()).isFalse();
        assertThat(unregisteredLatch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(refreshService, times(1)).unregister(credentialsProvider, registrationId);
        assertThat(c.isOpen()).isFalse();
    }
}
