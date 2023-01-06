// Copyright (c) 2019-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    public void callingBasicCancelForUnknownConsumerThrowsException() throws Exception {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        ChannelN channel = new ChannelN(connection, 1, consumerWorkService);
        assertThatThrownBy(() ->  channel.basicCancel("does-not-exist"))
            .isInstanceOf(IOException.class);
    }

    @Test
    public void qosShouldBeUnsignedShort() {
        AMQConnection connection = Mockito.mock(AMQConnection.class);
        AtomicReference<com.rabbitmq.client.AMQP.Basic.Qos> qosMethod = new AtomicReference<>();
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

    interface Consumer {

        void apply(int value) throws Exception;

    }

}
