// Copyright (c) 2019-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

<<<<<<< HEAD
import com.rabbitmq.client.AMQP;import com.rabbitmq.client.Command;
=======
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.DefaultConsumer;
>>>>>>> 1c9a5c0 (Respond with `basic.cancel-ok` to the servers that support it)
import com.rabbitmq.client.Method;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChannelNTest {

    ConsumerWorkService consumerWorkService;
    ExecutorService executorService;

    @BeforeEach
    public void init() {
        executorService = Executors.newSingleThreadExecutor();
        consumerWorkService = new ConsumerWorkService(executorService, null, 1000, 1000);
    }

    @AfterEach
    public void tearDown() {
        consumerWorkService.shutdown();
        executorService.shutdownNow();
    }

    @Test
    public void serverBasicCancelForUnknownConsumerDoesNotThrowException() throws Exception {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);
        Method method = new AMQImpl.Basic.Cancel.Builder().consumerTag("does-not-exist").build();
        channel.processAsync(new AMQCommand(method));
    }

    @Test
<<<<<<< HEAD
    public void callingBasicCancelForUnknownConsumerThrowsException() throws Exception {
=======
    public void serverBasicCancelIsAnsweredWithCancelOkWhenBrokerAcceptsIt() throws Exception {
        TrafficListener trafficListener = Mockito.mock(TrafficListener.class);
        AMQConnection connection = connectionAcceptingConsumerCancelOk(true, trafficListener);
        ChannelN channel = channelWithConsumer(connection, "ctag");

        channel.processAsync(new AMQCommand(new AMQImpl.Basic.Cancel.Builder().consumerTag("ctag").build()));

        Mockito.verify(trafficListener, Mockito.times(1)).write(
            Mockito.argThat(c -> c.getMethod() instanceof AMQP.Basic.CancelOk));
    }

    @Test
    public void serverBasicCancelIsNotAnsweredWhenBrokerDoesNotAcceptCancelOk() throws Exception {
        TrafficListener trafficListener = Mockito.mock(TrafficListener.class);
        AMQConnection connection = connectionAcceptingConsumerCancelOk(false, trafficListener);
        ChannelN channel = channelWithConsumer(connection, "ctag");

        channel.processAsync(new AMQCommand(new AMQImpl.Basic.Cancel.Builder().consumerTag("ctag").build()));

        Mockito.verify(trafficListener, Mockito.never()).write(
            Mockito.argThat(c -> c.getMethod() instanceof AMQP.Basic.CancelOk));
    }

    @Test
    public void serverBasicCancelForUnknownConsumerIsAnsweredWithCancelOkWhenBrokerAcceptsIt() throws Exception {
        TrafficListener trafficListener = Mockito.mock(TrafficListener.class);
        AMQConnection connection = connectionAcceptingConsumerCancelOk(true, trafficListener);
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);

        channel.processAsync(new AMQCommand(new AMQImpl.Basic.Cancel.Builder().consumerTag("does-not-exist").build()));

        Mockito.verify(trafficListener, Mockito.times(1)).write(
            Mockito.argThat(c -> c.getMethod() instanceof AMQP.Basic.CancelOk));
    }

    @Test
    public void serverBasicCancelForUnknownConsumerIsNotAnsweredWhenBrokerDoesNotAcceptCancelOk() throws Exception {
        TrafficListener trafficListener = Mockito.mock(TrafficListener.class);
        AMQConnection connection = connectionAcceptingConsumerCancelOk(false, trafficListener);
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);

        channel.processAsync(new AMQCommand(new AMQImpl.Basic.Cancel.Builder().consumerTag("does-not-exist").build()));

        Mockito.verify(trafficListener, Mockito.never()).write(Mockito.any(Command.class));
    }

    @Test
    public void callingBasicCancelForUnknownConsumerDoesNotThrowException() throws Exception {
>>>>>>> 1c9a5c0 (Respond with `basic.cancel-ok` to the servers that support it)
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);
        assertThatThrownBy(() ->  channel.basicCancel("does-not-exist"))
            .isInstanceOf(IOException.class);
    }

    @Test
    public void qosShouldBeUnsignedShort() {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        AtomicReference<AMQP.Basic.Qos> qosMethod = new AtomicReference<>();
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService) {
            @Override
            public AMQCommand exnWrappingRpc(Method m) {
                qosMethod.set((com.rabbitmq.client.AMQP.Basic.Qos) m);
                return null;
            }
        };
        class TestConfig {
            int value;
            Consumer call;
            int expected;

            public TestConfig(int value, Consumer call, int expected) {
                this.value = value;
                this.call = call;
                this.expected = expected;
            }
        }
        Consumer qos = value -> channel.basicQos(value);
        Consumer qosGlobal = value -> channel.basicQos(value, true);
        Consumer qosPrefetchSize = value -> channel.basicQos(10, value, true);
        Stream.of(
                new TestConfig(-1, qos, 0), new TestConfig(65536, qos, 65535),
                new TestConfig(10, qos, 10), new TestConfig(0, qos, 0)
        ).flatMap(config -> Stream.of(config, new TestConfig(config.value, qosGlobal, config.expected), new TestConfig(config.value, qosPrefetchSize, config.expected)))
                .forEach(config -> {
                    try {
                        assertThat(qosMethod.get()).isNull();
                        config.call.apply(config.value);
                        assertThat(qosMethod.get()).isNotNull();
                        assertThat(qosMethod.get().getPrefetchCount()).isEqualTo(config.expected);
                        qosMethod.set(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    @Test
    public void confirmSelectOnlySendsRPCCallOnce() throws Exception {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        TrafficListener trafficListener = Mockito.mock(TrafficListener.class);

        Mockito.when(connection.getTrafficListener()).thenReturn(trafficListener);

        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);

        new Thread(() -> {
            try {
                Thread.sleep(15);
                channel.handleCompleteInboundCommand(new AMQCommand(new AMQImpl.Confirm.SelectOk()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        assertNotNull(channel.confirmSelect());
        assertNotNull(channel.confirmSelect());
        Mockito.verify(trafficListener, Mockito.times(1)).write(Mockito.any(Command.class));
    }

    private AMQConnection connectionAcceptingConsumerCancelOk(
        boolean accepts, TrafficListener trafficListener) {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        Mockito.when(connection.getTrafficListener()).thenReturn(trafficListener);
        Mockito.when(connection.doesBrokerAcceptClientSentBasicCancelOk()).thenReturn(accepts);
        return connection;
    }

    private ChannelN channelWithConsumer(AMQConnection connection, String consumerTag) throws Exception {
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);
        new Thread(() -> {
            try {
                Thread.sleep(15);
                channel.handleCompleteInboundCommand(
                    new AMQCommand(new AMQImpl.Basic.ConsumeOk(consumerTag)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        channel.basicConsume("q", false, consumerTag, new DefaultConsumer(channel));
        return channel;
    }

    interface Consumer {

        void apply(int value) throws Exception;

    }

}
