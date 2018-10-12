// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.TrafficListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
@RunWith(Parameterized.class)
public class TrafficListenerTest {

    @Parameterized.Parameter
    public Consumer<ConnectionFactory> configurator;

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[] { automaticRecoveryEnabled(), automaticRecoveryDisabled() };
    }

    static Consumer<ConnectionFactory> automaticRecoveryEnabled() {
        return cf -> cf.setAutomaticRecoveryEnabled(true);
    }

    static Consumer<ConnectionFactory> automaticRecoveryDisabled() {
        return cf -> cf.setAutomaticRecoveryEnabled(false);
    }

    @Test
    public void trafficListenerIsCalled() throws Exception {
        ConnectionFactory cf = TestUtils.connectionFactory();
        TestTrafficListener testTrafficListener = new TestTrafficListener();
        cf.setTrafficListener(testTrafficListener);
        configurator.accept(cf);
        try (Connection c = cf.newConnection()) {
            Channel ch = c.createChannel();
            String queue = ch.queueDeclare().getQueue();
            CountDownLatch latch = new CountDownLatch(1);
            ch.basicConsume(queue, true,
                (consumerTag, message) -> latch.countDown(), consumerTag -> {
                });
            String messageContent = UUID.randomUUID().toString();
            ch.basicPublish("", queue, null, messageContent.getBytes());
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, testTrafficListener.outboundContent.size());
            assertEquals(messageContent, testTrafficListener.outboundContent.get(0));
            assertEquals(1, testTrafficListener.inboundContent.size());
            assertEquals(messageContent, testTrafficListener.inboundContent.get(0));
        }
    }

    private static class TestTrafficListener implements TrafficListener {

        final List<String> outboundContent = new CopyOnWriteArrayList<>();
        final List<String> inboundContent = new CopyOnWriteArrayList<>();

        @Override
        public void write(Command outboundCommand) {
            if (outboundCommand.getMethod() instanceof AMQP.Basic.Publish) {
                outboundContent.add(new String(outboundCommand.getContentBody()));
            }
        }

        @Override
        public void read(Command inboundCommand) {
            if (inboundCommand.getMethod() instanceof AMQP.Basic.Deliver) {
                inboundContent.add(new String(inboundCommand.getContentBody()));
            }
        }
    }
}
