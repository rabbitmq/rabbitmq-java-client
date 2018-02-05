// Copyright (c) 2018-Present Pivotal Software, Inc.  All rights reserved.
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
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test to trigger and check the fix of https://github.com/rabbitmq/rabbitmq-java-client/issues/341.
 * Conditions:
 * - client registers consumer and a call QoS after
 * - client get many messages and the consumer is slow
 * - the work pool queue is full, the reading thread is stuck
 * - more messages come from the network and saturates the TCP buffer
 * - the connection dies but the client doesn't detect it
 * - acks of messages fail
 * - connection recovery is never triggered
 * <p>
 * The fix consists in triggering connection recovery when writing
 * to the socket fails. As the socket is dead, the closing
 * sequence can take some time, hence the setup of the shutdown
 * listener, which avoids waiting for the socket termination by
 * the OS (can take 15 minutes on linux).
 */
public class NoAutoRecoveryWhenTcpWindowIsFullTest {

    private static final int QOS_PREFETCH = 64;
    private static final int NUM_MESSAGES_TO_PRODUCE = 100000;
    private static final int MESSAGE_PROCESSING_TIME_MS = 3000;

    private ExecutorService dispatchingService;
    private ExecutorService producerService;
    private ExecutorService shutdownService;
    private AutorecoveringConnection producingConnection;
    private AutorecoveringChannel producingChannel;
    private AutorecoveringConnection consumingConnection;
    private AutorecoveringChannel consumingChannel;

    private CountDownLatch consumerRecoverOkLatch;

    @Before
    public void setUp() throws Exception {
        dispatchingService = Executors.newSingleThreadExecutor();
        shutdownService = Executors.newSingleThreadExecutor();
        producerService = Executors.newSingleThreadExecutor();
        final ConnectionFactory factory = TestUtils.connectionFactory();
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        // we try to set the lower values for closing timeouts, etc.
        // this makes the test execute faster.
        factory.setShutdownExecutor(shutdownService);
        factory.setShutdownTimeout(10000);
        factory.setRequestedHeartbeat(5);
        factory.setSharedExecutor(dispatchingService);
        factory.setNetworkRecoveryInterval(1000);

        producingConnection = (AutorecoveringConnection) factory.newConnection("Producer Connection");
        producingChannel = (AutorecoveringChannel) producingConnection.createChannel();
        consumingConnection = (AutorecoveringConnection) factory.newConnection("Consuming Connection");
        consumingChannel = (AutorecoveringChannel) consumingConnection.createChannel();

        consumerRecoverOkLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() throws IOException {
        closeConnectionIfOpen(consumingConnection);
        closeConnectionIfOpen(producingConnection);

        dispatchingService.shutdownNow();
        producerService.shutdownNow();
        shutdownService.shutdownNow();
    }

    @Test
    public void failureAndRecovery() throws IOException, InterruptedException {
        if (TestUtils.USE_NIO) {
            return;
        }
        final String queue = UUID.randomUUID().toString();

        final CountDownLatch latch = new CountDownLatch(1);

        consumingConnection.addRecoveryListener(new RecoveryListener() {

            @Override
            public void handleRecovery(Recoverable recoverable) {
            }

            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                latch.countDown();
            }
        });

        declareQueue(producingChannel, queue);
        produceMessagesInBackground(producingChannel, queue);
        startConsumer(queue);

        assertThat(
            "Connection should have been closed and should have recovered by now",
            latch.await(60, TimeUnit.SECONDS), is(true)
        );

        assertThat(
            "Consumer should have recovered by now",
            latch.await(5, TimeUnit.SECONDS), is(true)
        );
    }

    private void closeConnectionIfOpen(Connection connection) throws IOException {
        if (connection.isOpen()) {
            connection.close();
        }
    }

    private void declareQueue(final Channel channel, final String queue) throws IOException {
        final Map<String, Object> queueArguments = new HashMap<String, Object>();
        channel.queueDeclare(queue, false, false, false, queueArguments);
    }

    private void produceMessagesInBackground(final Channel channel, final String queue) throws IOException {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(1).build();
        producerService.submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                for (int i = 0; i < NUM_MESSAGES_TO_PRODUCE; i++) {
                    channel.basicPublish("", queue, false, properties, ("MSG NUM" + i).getBytes());
                }
                closeConnectionIfOpen(producingConnection);
                return null;
            }
        });
    }

    private void startConsumer(final String queue) throws IOException {
        consumingChannel.basicConsume(queue, false, "", false, false, null, new DefaultConsumer(consumingChannel) {

            @Override
            public void handleRecoverOk(String consumerTag) {
                consumerRecoverOkLatch.countDown();
            }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                consumerWork();
                try {
                    consumingChannel.basicAck(envelope.getDeliveryTag(), false);
                } catch (Exception e) {
                    // application should handle writing exceptions
                }
            }
        });
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        consumingChannel.basicQos(QOS_PREFETCH);
    }

    private void consumerWork() {
        try {
            Thread.sleep(MESSAGE_PROCESSING_TIME_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

