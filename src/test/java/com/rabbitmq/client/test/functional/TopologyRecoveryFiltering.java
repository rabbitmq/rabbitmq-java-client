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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.impl.recovery.RecordedBinding;
import com.rabbitmq.client.impl.recovery.RecordedConsumer;
import com.rabbitmq.client.impl.recovery.RecordedExchange;
import com.rabbitmq.client.impl.recovery.RecordedQueue;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.waitAtMost;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class TopologyRecoveryFiltering extends BrokerTestCase {

    static {
        System.setProperty("rabbitmqctl.bin", "/home/acogoluegnes/Downloads/rabbitmq_server-3.7.7/sbin/rabbitmqctl");
        System.setProperty("test-broker.A.nodename", "rabbit@acogoluegnes-xps");
    }

    String[] exchangesToDelete = new String[] {
        "recovered.exchange", "filtered.exchange", "topology.recovery.exchange"
    };
    String[] queuesToDelete = new String[] {
        "topology.recovery.queue.1", "topology.recovery.queue.2"
    };
    Connection c;

    private static boolean sendAndConsumeMessage(String exchange, String routingKey, String queue, Connection c)
        throws IOException, TimeoutException, InterruptedException {
        Channel ch = c.createChannel();
        try {
            ch.confirmSelect();
            final CountDownLatch latch = new CountDownLatch(1);
            ch.basicConsume(queue, true, new DefaultConsumer(ch) {

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    latch.countDown();
                }
            });
            ch.basicPublish(exchange, routingKey, null, "".getBytes());
            ch.waitForConfirmsOrDie(5000);
            return latch.await(5, TimeUnit.SECONDS);
        } finally {
            if (ch != null && ch.isOpen()) {
                ch.close();
            }
        }
    }

    private static boolean resourceExists(Callable<Channel> callback) throws Exception {
        Channel declarePassiveChannel = null;
        try {
            declarePassiveChannel = callback.call();
            return true;
        } catch (IOException e) {
            if (e.getCause() instanceof ShutdownSignalException) {
                ShutdownSignalException cause = (ShutdownSignalException) e.getCause();
                if (cause.getReason() instanceof AMQP.Channel.Close) {
                    if (((AMQP.Channel.Close) cause.getReason()).getReplyCode() == 404) {
                        return false;
                    } else {
                        throw e;
                    }
                }
                return false;
            } else {
                throw e;
            }
        } finally {
            if (declarePassiveChannel != null && declarePassiveChannel.isOpen()) {
                declarePassiveChannel.close();
            }
        }
    }

    private static boolean queueExists(final String queue, final Connection connection) throws Exception {
        return resourceExists(new Callable<Channel>() {

            @Override
            public Channel call() throws Exception {
                Channel channel = connection.createChannel();
                channel.queueDeclarePassive(queue);
                return channel;
            }
        });
    }

    private static boolean exchangeExists(final String exchange, final Connection connection) throws Exception {
        return resourceExists(new Callable<Channel>() {

            @Override
            public Channel call() throws Exception {
                Channel channel = connection.createChannel();
                channel.exchangeDeclarePassive(exchange);
                return channel;
            }
        });
    }

    private static void closeAndWaitForRecovery(RecoverableConnection connection) throws IOException, InterruptedException {
        CountDownLatch latch = prepareForRecovery(connection);
        Host.closeConnection((NetworkConnection) connection);
        wait(latch);
    }

    private static CountDownLatch prepareForRecovery(Connection conn) {
        final CountDownLatch latch = new CountDownLatch(1);
        ((AutorecoveringConnection) conn).addRecoveryListener(new RecoveryListener() {

            @Override
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }

            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                // No-op
            }
        });
        return latch;
    }

    private static void wait(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(20, TimeUnit.SECONDS));
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setTopologyRecoveryFilter(new SimpleTopologyRecoveryFilter());
        connectionFactory.setNetworkRecoveryInterval(1000);
        return connectionFactory;
    }

    @Override
    protected void createResources() throws IOException, TimeoutException {
        super.createResources();
        c = connectionFactory.newConnection();
        deleteExchanges(exchangesToDelete);
        deleteQueues(queuesToDelete);
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        c.close();
        deleteExchanges(exchangesToDelete);
        deleteQueues(queuesToDelete);
    }

    @Test
    public void topologyRecoveryFilteringExchangesAndQueues() throws Exception {
        Channel ch = c.createChannel();
        ch.exchangeDeclare("recovered.exchange", "direct");
        ch.exchangeDeclare("filtered.exchange", "direct");
        ch.queueDeclare("recovered.queue", false, true, true, null);
        ch.queueDeclare("filtered.queue", false, true, true, null);

        // to check whether the other connection recovers them or not
        channel.exchangeDelete("recovered.exchange");
        channel.exchangeDelete("filtered.exchange");

        closeAndWaitForRecovery((RecoverableConnection) c);

        assertTrue(exchangeExists("recovered.exchange", c));
        assertFalse(exchangeExists("filtered.exchange", c));

        assertTrue(queueExists("recovered.queue", c));
        assertFalse(queueExists("filtered.queue", c));
    }

    @Test
    public void topologyRecoveryFilteringBindings() throws Exception {
        Channel ch = c.createChannel();

        ch.exchangeDeclare("topology.recovery.exchange", "direct");
        ch.queueDeclare("topology.recovery.queue.1", false, false, false, null);
        ch.queueDeclare("topology.recovery.queue.2", false, false, false, null);
        ch.queueBind("topology.recovery.queue.1", "topology.recovery.exchange", "recovered.binding");
        ch.queueBind("topology.recovery.queue.2", "topology.recovery.exchange", "filtered.binding");

        // to check whether the other connection recovers them or not
        channel.queueUnbind("topology.recovery.queue.1", "topology.recovery.exchange", "recovered.binding");
        channel.queueUnbind("topology.recovery.queue.2", "topology.recovery.exchange", "filtered.binding");

        closeAndWaitForRecovery((RecoverableConnection) c);

        assertTrue("The message should have been received by now", sendAndConsumeMessage(
            "topology.recovery.exchange", "recovered.binding", "topology.recovery.queue.1", c
        ));
        assertFalse("Binding shouldn't recover, no messages should have been received", sendAndConsumeMessage(
            "topology.recovery.exchange", "filtered.binding", "topology.recovery.queue.2", c
        ));
    }

    @Test
    public void topologyRecoveryFilteringConsumers() throws Exception {
        Channel ch = c.createChannel();

        ch.exchangeDeclare("topology.recovery.exchange", "direct");
        ch.queueDeclare("topology.recovery.queue.1", false, false, false, null);
        ch.queueDeclare("topology.recovery.queue.2", false, false, false, null);
        ch.queueBind("topology.recovery.queue.1", "topology.recovery.exchange", "recovered.consumer");
        ch.queueBind("topology.recovery.queue.2", "topology.recovery.exchange", "filtered.consumer");

        final AtomicInteger recoveredConsumerMessageCount = new AtomicInteger(0);
        ch.basicConsume("topology.recovery.queue.1", true, "recovered.consumer", new DefaultConsumer(ch) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                recoveredConsumerMessageCount.incrementAndGet();
            }
        });
        ch.basicPublish("topology.recovery.exchange", "recovered.consumer", null, "".getBytes());
        waitAtMost(5, TimeUnit.SECONDS).untilAtomic(recoveredConsumerMessageCount, is(1));

        final AtomicInteger filteredConsumerMessageCount = new AtomicInteger(0);
        final CountDownLatch filteredConsumerLatch = new CountDownLatch(2);
        ch.basicConsume("topology.recovery.queue.2", true, "filtered.consumer", new DefaultConsumer(ch) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                filteredConsumerMessageCount.incrementAndGet();
                filteredConsumerLatch.countDown();
            }
        });
        ch.basicPublish("topology.recovery.exchange", "filtered.consumer", null, "".getBytes());
        waitAtMost(5, TimeUnit.SECONDS).untilAtomic(filteredConsumerMessageCount, is(1));

        closeAndWaitForRecovery((RecoverableConnection) c);

        int initialCount = recoveredConsumerMessageCount.get();
        ch.basicPublish("topology.recovery.exchange", "recovered.consumer", null, "".getBytes());
        waitAtMost(5, TimeUnit.SECONDS).untilAtomic(recoveredConsumerMessageCount, is(initialCount + 1));

        ch.basicPublish("topology.recovery.exchange", "filtered.consumer", null, "".getBytes());
        assertFalse("Consumer shouldn't recover, no extra messages should have been received",
            filteredConsumerLatch.await(5, TimeUnit.SECONDS));
    }

    private static class SimpleTopologyRecoveryFilter implements TopologyRecoveryFilter {

        @Override
        public boolean filterExchange(RecordedExchange recordedExchange) {
            return !recordedExchange.getName().contains("filtered");
        }

        @Override
        public boolean filterQueue(RecordedQueue recordedQueue) {
            return !recordedQueue.getName().contains("filtered");
        }

        @Override
        public boolean filterBinding(RecordedBinding recordedBinding) {
            return !recordedBinding.getRoutingKey().contains("filtered");
        }

        @Override
        public boolean filterConsumer(RecordedConsumer recordedConsumer) {
            return !recordedConsumer.getConsumerTag().contains("filtered");
        }
    }
}
