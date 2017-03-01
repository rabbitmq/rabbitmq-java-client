// Copyright (c) 2017 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

public class LambdaCallbackTest extends BrokerTestCase {

    String queue;

    @Override
    protected void createResources() throws IOException, TimeoutException {
        queue = channel.queueDeclare(UUID.randomUUID().toString(), true, false, false, null).getQueue();
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(queue);
        try {
            unblock();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test public void shutdownListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        try(Connection connection = TestUtils.connectionFactory().newConnection()) {
            connection.addShutdownListener(cause -> latch.countDown());
            Channel channel = connection.createChannel();
            channel.addShutdownListener(cause -> latch.countDown());
        }
        assertTrue("Connection closed, shutdown listeners should have been called", latch.await(1, TimeUnit.SECONDS));
    }

    @Test public void confirmListener() throws Exception {
        channel.confirmSelect();
        CountDownLatch latch = new CountDownLatch(1);
        channel.addConfirmListener(
            (deliveryTag, multiple) -> latch.countDown(),
            (deliveryTag, multiple) -> {}
        );
        channel.basicPublish("", "whatever", null, "dummy".getBytes());
        assertTrue("Should have received publisher confirm", latch.await(1, TimeUnit.SECONDS));
    }

    @Test public void returnListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        channel.addReturnListener(returnMessage -> latch.countDown());
        channel.basicPublish("", "notlikelytoexist", true, null, "dummy".getBytes());
        assertTrue("Should have received returned message", latch.await(1, TimeUnit.SECONDS));
    }

    @Test public void blockedListener() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        try(Connection connection = TestUtils.connectionFactory().newConnection()) {
            connection.addBlockedListener(
                reason -> {
                    try {
                        unblock();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            },
                () -> latch.countDown()
            );
            block();
            Channel ch = connection.createChannel();
            ch.basicPublish("", "", null, "dummy".getBytes());
            assertTrue("Should have been blocked and unblocked", latch.await(10, TimeUnit.SECONDS));
        }
    }

    @Test public void basicConsumeDeliverCancel() throws Exception {
        try(Connection connection = TestUtils.connectionFactory().newConnection()) {
            final CountDownLatch consumingLatch = new CountDownLatch(1);
            final CountDownLatch cancelLatch = new CountDownLatch(1);
            Channel consumingChannel = connection.createChannel();
            consumingChannel.basicConsume(queue, true,
                (consumerTag, delivery) -> consumingLatch.countDown(),
                consumerTag -> cancelLatch.countDown()
            );
            this.channel.basicPublish("", queue, null, "dummy".getBytes());
            assertTrue("deliver callback should have been called", consumingLatch.await(1, TimeUnit.SECONDS));
            this.channel.queueDelete(queue);
            assertTrue("cancel callback should have been called", cancelLatch.await(1, TimeUnit.SECONDS));
        }
    }

    @Test public void basicConsumeDeliverShutdown() throws Exception {
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        try(Connection connection = TestUtils.connectionFactory().newConnection()) {
            final CountDownLatch consumingLatch = new CountDownLatch(1);
            Channel consumingChannel = connection.createChannel();
            consumingChannel.basicConsume(queue, true,
                (consumerTag, delivery) -> consumingLatch.countDown(),
                (consumerTag, sig) -> shutdownLatch.countDown()
            );
            this.channel.basicPublish("", queue, null, "dummy".getBytes());
            assertTrue("deliver callback should have been called", consumingLatch.await(1, TimeUnit.SECONDS));
        }
        assertTrue("shutdown callback should have been called", shutdownLatch.await(1, TimeUnit.SECONDS));
    }

    @Test public void basicConsumeCancelDeliverShutdown() throws Exception {
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        try(Connection connection = TestUtils.connectionFactory().newConnection()) {
            final CountDownLatch consumingLatch = new CountDownLatch(1);
            Channel consumingChannel = connection.createChannel();
            // not both cancel and shutdown callback can be called on the same consumer
            // testing just shutdown
            consumingChannel.basicConsume(queue, true,
                (consumerTag, delivery) -> consumingLatch.countDown(),
                (consumerTag) -> { },
                (consumerTag, sig) -> shutdownLatch.countDown()
            );
            this.channel.basicPublish("", queue, null, "dummy".getBytes());
            assertTrue("deliver callback should have been called", consumingLatch.await(1, TimeUnit.SECONDS));
        }
        assertTrue("shutdown callback should have been called", shutdownLatch.await(1, TimeUnit.SECONDS));
    }

}
