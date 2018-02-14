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
import com.rabbitmq.client.DefaultSocketConfigurator;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
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
 * Test to trigger and check the fix of rabbitmq/rabbitmq-java-client#341,
 * which can be summarized as
 *
 * <ul>
 *   <li>client registers a slow consumer in automatic acknowledgement mode</li>
 *   <li>there's a fast enough publisher</li>
 *   <li>the consumer gets flooded with deliveries</li>
 *   <li>the work pool queue is full, the reading thread is stuck</li>
 *   <li>more messages come from the network and it fills up the TCP buffer</li>
 *   <li>the connection is closed by the server due to missed heartbeats but the client doesn't detect it</li>
 *   <li>a write operation fails because the socket is closed</li>
 *   <li>connection recovery is never triggered</li>
 * </ul>
 *
 * <p>
 * The fix consists in triggering connection recovery when writing
 * to the socket fails.
 * </p>
 */
public class NoAutoRecoveryWhenTcpWindowIsFullTest {

    private static final int NUM_MESSAGES_TO_PRODUCE = 50000;
    private static final int MESSAGE_PROCESSING_TIME_MS = 3000;
    private static final byte[] MESSAGE_CONTENT = ("MESSAGE CONTENT " + NUM_MESSAGES_TO_PRODUCE).getBytes();

    private ExecutorService executorService;
    private AutorecoveringConnection producingConnection;
    private AutorecoveringChannel producingChannel;
    private AutorecoveringConnection consumingConnection;
    private AutorecoveringChannel consumingChannel;

    private CountDownLatch consumerOkLatch;

    @Before
    public void setUp() throws Exception {
        // we need several threads to publish, dispatch deliveries, handle RPC responses, etc.
        executorService = Executors.newFixedThreadPool(10);
        final ConnectionFactory factory = TestUtils.connectionFactory();
        factory.setSocketConfigurator(new DefaultSocketConfigurator() {

            /* default value on Linux */
            int DEFAULT_RECEIVE_BUFFER_SIZE = 43690;

            @Override
            public void configure(Socket socket) throws IOException {
                super.configure(socket);
                socket.setReceiveBufferSize(DEFAULT_RECEIVE_BUFFER_SIZE);
            }
        });
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        // we try to set the lower values for closing timeouts, etc.
        // this makes the test execute faster.
        factory.setRequestedHeartbeat(5);
        factory.setSharedExecutor(executorService);
        // we need the shutdown executor: channel shutting down depends on the work pool,
        // which is full. Channel shutting down will time out with the shutdown executor.
        factory.setShutdownExecutor(executorService);
        factory.setNetworkRecoveryInterval(2000);

        if (TestUtils.USE_NIO) {
            factory.setWorkPoolTimeout(10 * 1000);
            factory.setNioParams(new NioParams().setWriteQueueCapacity(10 * 1000 * 1000).setNbIoThreads(4));
        }

        producingConnection = (AutorecoveringConnection) factory.newConnection("Producer Connection");
        producingChannel = (AutorecoveringChannel) producingConnection.createChannel();
        consumingConnection = (AutorecoveringConnection) factory.newConnection("Consuming Connection");
        consumingChannel = (AutorecoveringChannel) consumingConnection.createChannel();

        consumerOkLatch = new CountDownLatch(2);
    }

    @After
    public void tearDown() throws IOException {
        closeConnectionIfOpen(consumingConnection);
        closeConnectionIfOpen(producingConnection);

        executorService.shutdownNow();
    }

    @Test
    public void failureAndRecovery() throws IOException, InterruptedException {
        final String queue = UUID.randomUUID().toString();

        final CountDownLatch recoveryLatch = new CountDownLatch(1);

        consumingConnection.addRecoveryListener(new RecoveryListener() {

            @Override
            public void handleRecovery(Recoverable recoverable) {
                recoveryLatch.countDown();
            }

            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
            }
        });

        declareQueue(producingChannel, queue);
        produceMessagesInBackground(producingChannel, queue);
        startConsumer(queue);

        assertThat(
            "Connection should have been closed and should have recovered by now",
            recoveryLatch.await(60, TimeUnit.SECONDS), is(true)
        );

        assertThat(
            "Consumer should have recovered by now",
            consumerOkLatch.await(5, TimeUnit.SECONDS), is(true)
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
        executorService.submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                for (int i = 0; i < NUM_MESSAGES_TO_PRODUCE; i++) {
                    channel.basicPublish("", queue, false, properties, MESSAGE_CONTENT);
                }
                closeConnectionIfOpen(producingConnection);
                return null;
            }
        });
    }

    private void startConsumer(final String queue) throws IOException {
        consumingChannel.basicConsume(queue, true, new DefaultConsumer(consumingChannel) {

            @Override
            public void handleConsumeOk(String consumerTag) {
                consumerOkLatch.countDown();
            }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                consumerWork();
                try {
                    consumingChannel.basicPublish("", "", null, "".getBytes());
                } catch (Exception e) {
                    // application should handle writing exceptions
                }
            }
        });
    }

    private void consumerWork() {
        try {
            Thread.sleep(MESSAGE_PROCESSING_TIME_MS);
        } catch (InterruptedException e) {
        }
    }
}

