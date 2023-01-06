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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class Metrics extends BrokerTestCase {

    public static Object[] data() {
        return new Object[] { createConnectionFactory(), createAutoRecoveryConnectionFactory() };
    }

    static final String QUEUE = "metrics.queue";

    @Override
    protected void createResources() throws IOException {
        channel.queueDeclare(QUEUE, true, false, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(QUEUE);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void metrics(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = connectionFactory.newConnection();
            assertThat(metrics.getConnections().getCount()).isEqualTo(1L);

            connection1.createChannel();
            connection1.createChannel();
            Channel channel = connection1.createChannel();
            assertThat(metrics.getChannels().getCount()).isEqualTo(3L);

            sendMessage(channel);
            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(1L);
            sendMessage(channel);
            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(2L);

            channel.basicGet(QUEUE, true);
            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(1L);
            channel.basicGet(QUEUE, true);
            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(2L);
            channel.basicGet(QUEUE, true);
            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(2L);

            connection2 = connectionFactory.newConnection();
            assertThat(metrics.getConnections().getCount()).isEqualTo(2L);

            connection2.createChannel();
            channel = connection2.createChannel();
            assertThat(metrics.getChannels().getCount()).isEqualTo(3L+2L);
            sendMessage(channel);
            sendMessage(channel);
            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(2L+2L);

            channel.basicGet(QUEUE, true);
            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(2L+1L);

            channel.basicConsume(QUEUE, true, new DefaultConsumer(channel));
            waitAtMost(timeout(), () -> metrics.getConsumedMessages().getCount() == 2L+1L+1L);

            safeClose(connection1);
            waitAtMost(timeout(), () -> metrics.getConnections().getCount() == 1L);
            waitAtMost(timeout(), () -> metrics.getChannels().getCount() == 2L);

            safeClose(connection2);
            waitAtMost(timeout(), () -> metrics.getConnections().getCount() == 0L);
            waitAtMost(timeout(), () -> metrics.getChannels().getCount() == 0L);

            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(0L);
            assertThat(metrics.getRejectedMessages().getCount()).isEqualTo(0L);

        } finally {
            safeClose(connection1);
            safeClose(connection2);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void metricsPublisherUnrouted(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);
        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();
            channel.confirmSelect();
            assertThat(metrics.getPublishUnroutedMessages().getCount()).isEqualTo(0L);
            // when
            channel.basicPublish(
                    "amq.direct",
                    "any-unroutable-routing-key",
                    /* basic.return will be sent back only if the message is mandatory */ true,
                    MessageProperties.MINIMAL_BASIC,
                    "any-message".getBytes()
            );
            // then
            waitAtMost(timeout(), () -> metrics.getPublishUnroutedMessages().getCount() == 1L);
        } finally {
            safeClose(connection);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void metricsPublisherAck(ConnectionFactory connectionFactory) throws IOException, TimeoutException, InterruptedException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);
        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();
            channel.confirmSelect();
            assertThat(metrics.getPublishAcknowledgedMessages().getCount()).isEqualTo(0L);
            MultipleAckConsumer consumer = new MultipleAckConsumer(channel, false);
            channel.basicConsume(QUEUE, false, consumer);
            CountDownLatch confirmedLatch = new CountDownLatch(1);
            channel.addConfirmListener((deliveryTag, multiple) -> confirmedLatch.countDown(),
                (deliveryTag, multiple) -> { });
            // when
            sendMessage(channel);
            assertThat(confirmedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            // then
            waitAtMost(Duration.ofSeconds(5), () -> metrics.getPublishAcknowledgedMessages().getCount() == 1);
            waitAtMost(() -> consumer.ackedCount() == 1);
        } finally {
            safeClose(connection);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void metricsAck(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel channel1 = connection.createChannel();
            Channel channel2 = connection.createChannel();

            sendMessage(channel1);
            GetResponse getResponse = channel1.basicGet(QUEUE, false);
            channel1.basicAck(getResponse.getEnvelope().getDeliveryTag(), false);
            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(1L);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L);

            // basicGet / basicAck
            sendMessage(channel1);
            sendMessage(channel2);
            sendMessage(channel1);
            sendMessage(channel2);
            sendMessage(channel1);
            sendMessage(channel2);

            GetResponse response1 = channel1.basicGet(QUEUE, false);
            GetResponse response2 = channel2.basicGet(QUEUE, false);
            GetResponse response3 = channel1.basicGet(QUEUE, false);
            GetResponse response4 = channel2.basicGet(QUEUE, false);
            GetResponse response5 = channel1.basicGet(QUEUE, false);
            GetResponse response6 = channel2.basicGet(QUEUE, false);

            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo(1L+6L);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L);

            channel1.basicAck(response5.getEnvelope().getDeliveryTag(), false);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L+1L);
            channel1.basicAck(response3.getEnvelope().getDeliveryTag(), true);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L+1L+2L);

            channel2.basicAck(response2.getEnvelope().getDeliveryTag(), true);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L+(1L+2L)+1L);
            channel2.basicAck(response6.getEnvelope().getDeliveryTag(), true);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(1L+(1L+2L)+1L+2L);

            long alreadySentMessages = 1+(1+2)+1+2;

            // basicConsume / basicAck
            channel1.basicConsume(QUEUE, false, new MultipleAckConsumer(channel1, false));
            channel1.basicConsume(QUEUE, false, new MultipleAckConsumer(channel1, true));
            channel2.basicConsume(QUEUE, false, new MultipleAckConsumer(channel2, false));
            channel2.basicConsume(QUEUE, false, new MultipleAckConsumer(channel2, true));

            int nbMessages = 10;
            for(int i = 0; i < nbMessages; i++) {
                sendMessage(i%2 == 0 ? channel1 : channel2);
            }

            waitAtMost(timeout(), () -> metrics.getConsumedMessages().getCount() == alreadySentMessages+nbMessages);

            waitAtMost(timeout(), () -> metrics.getAcknowledgedMessages().getCount() == alreadySentMessages+nbMessages);

        } finally {
            safeClose(connection);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void metricsReject(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();

            sendMessage(channel);
            sendMessage(channel);
            sendMessage(channel);

            GetResponse response1 = channel.basicGet(QUEUE, false);
            GetResponse response2 = channel.basicGet(QUEUE, false);
            GetResponse response3 = channel.basicGet(QUEUE, false);

            channel.basicReject(response2.getEnvelope().getDeliveryTag(), false);
            assertThat(metrics.getRejectedMessages().getCount()).isEqualTo(1L);

            channel.basicNack(response3.getEnvelope().getDeliveryTag(), true, false);
            assertThat(metrics.getRejectedMessages().getCount()).isEqualTo(1L+2L);
        } finally {
            safeClose(connection);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void multiThreadedMetricsStandardConnection(ConnectionFactory connectionFactory) throws InterruptedException, TimeoutException, IOException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);
        int nbConnections = 3;
        int nbChannelsPerConnection = 5;
        int nbChannels = nbConnections * nbChannelsPerConnection;
        long nbOfMessages = 100;
        int nbTasks = nbChannels; // channel are not thread-safe

        Random random = new Random();

        // create connections
        Connection [] connections = new Connection[nbConnections];
        ExecutorService executorService = Executors.newFixedThreadPool(nbTasks);
        try {
            Channel [] channels = new Channel[nbChannels];
            for(int i = 0; i < nbConnections; i++) {
                connections[i] = connectionFactory.newConnection();
                for(int j = 0; j < nbChannelsPerConnection; j++) {
                    Channel channel = connections[i].createChannel();
                    channel.basicQos(1);
                    channels[i * nbChannelsPerConnection + j] = channel;
                }
            }

            // consume messages without ack
            for(int i = 0; i < nbOfMessages; i++) {
                sendMessage(channels[random.nextInt(nbChannels)]);
            }


            List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
            for(int i = 0; i < nbTasks; i++) {
                Channel channelForConsuming = channels[random.nextInt(nbChannels)];
                tasks.add(random.nextInt(10)%2 == 0 ?
                    new BasicGetTask(channelForConsuming, true) :
                    new BasicConsumeTask(channelForConsuming, true));
            }
            executorService.invokeAll(tasks);

            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getConsumedMessages().getCount() == nbOfMessages);
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo(0L);

            // to remove the listeners
            for(int i = 0; i < nbChannels; i++) {
                channels[i].close();
                Channel channel = connections[random.nextInt(nbConnections)].createChannel();
                channel.basicQos(1);
                channels[i] = channel;
            }

            // consume messages with ack
            for(int i = 0; i < nbOfMessages; i++) {
                sendMessage(channels[random.nextInt(nbChannels)]);
            }

            executorService.shutdownNow();

            executorService = Executors.newFixedThreadPool(nbTasks);
            tasks = new ArrayList<Callable<Void>>();
            for(int i = 0; i < nbTasks; i++) {
                Channel channelForConsuming = channels[i];
                tasks.add(random.nextBoolean() ?
                    new BasicGetTask(channelForConsuming, false) :
                    new BasicConsumeTask(channelForConsuming, false));
            }
            executorService.invokeAll(tasks);

            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(2*nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getConsumedMessages().getCount() == 2*nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getAcknowledgedMessages().getCount() == nbOfMessages);

            // to remove the listeners
            for(int i = 0; i < nbChannels; i++) {
                channels[i].close();
                Channel channel = connections[random.nextInt(nbConnections)].createChannel();
                channel.basicQos(1);
                channels[i] = channel;
            }

            // consume messages and reject them
            for(int i = 0; i < nbOfMessages; i++) {
                sendMessage(channels[random.nextInt(nbChannels)]);
            }

            executorService.shutdownNow();

            executorService = Executors.newFixedThreadPool(nbTasks);
            tasks = new ArrayList<Callable<Void>>();
            for(int i = 0; i < nbTasks; i++) {
                Channel channelForConsuming = channels[i];
                tasks.add(random.nextBoolean() ?
                    new BasicGetRejectTask(channelForConsuming) :
                    new BasicConsumeRejectTask(channelForConsuming));
            }
            executorService.invokeAll(tasks);

            assertThat(metrics.getPublishedMessages().getCount()).isEqualTo(3*nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getConsumedMessages().getCount() == 3*nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getAcknowledgedMessages().getCount() == nbOfMessages);
            waitAtMost(timeout(), () -> metrics.getRejectedMessages().getCount() == nbOfMessages);
        } finally {
            for (Connection connection : connections) {
                safeClose(connection);
            }
            executorService.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void errorInChannel(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();

            assertThat(metrics.getConnections().getCount()).isEqualTo(1L);
            assertThat(metrics.getChannels().getCount()).isEqualTo(1L);

            channel.basicPublish("unlikelynameforanexchange", "", null, "msg".getBytes("UTF-8"));

            waitAtMost(timeout(), () -> metrics.getChannels().getCount() == 0L);
            assertThat(metrics.getConnections().getCount()).isEqualTo(1L);
        } finally {
            safeClose(connection);
        }
    }

    @Test public void checkListenersWithAutoRecoveryConnection() throws Exception {
        ConnectionFactory connectionFactory = createConnectionFactory();
        connectionFactory.setNetworkRecoveryInterval(2000);
        connectionFactory.setAutomaticRecoveryEnabled(true);
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection(UUID.randomUUID().toString());

            Collection<?> shutdownHooks = getShutdownHooks(connection);
            assertThat(shutdownHooks.size()).isEqualTo(0);

            connection.createChannel();

            assertThat(metrics.getConnections().getCount()).isEqualTo(1L);
            assertThat(metrics.getChannels().getCount()).isEqualTo(1L);

            closeAndWaitForRecovery((AutorecoveringConnection) connection);

            assertThat(metrics.getConnections().getCount()).isEqualTo(1L);
            assertThat(metrics.getChannels().getCount()).isEqualTo(1L);

            assertThat(shutdownHooks.size()).isEqualTo(0);
        } finally {
            safeClose(connection);
        }
    }

    @Test public void checkAcksWithAutomaticRecovery() throws Exception {
        ConnectionFactory connectionFactory = createConnectionFactory();
        connectionFactory.setNetworkRecoveryInterval(2000);
        connectionFactory.setAutomaticRecoveryEnabled(true);
        StandardMetricsCollector metrics = new StandardMetricsCollector();
        connectionFactory.setMetricsCollector(metrics);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection(UUID.randomUUID().toString());

            Channel channel1 = connection.createChannel();
            AtomicInteger ackedMessages = new AtomicInteger(0);

            channel1.basicConsume(QUEUE, false, (consumerTag, message) -> {
                try {
                    channel1.basicAck(message.getEnvelope().getDeliveryTag(), false);
                    ackedMessages.incrementAndGet();
                } catch (Exception e) { }
            }, tag -> {});

            Channel channel2 = connection.createChannel();
            channel2.confirmSelect();
            int nbMessages = 10;
            for (int i = 0; i < nbMessages; i++) {
                sendMessage(channel2);
            }
            channel2.waitForConfirms(1000);

            closeAndWaitForRecovery((AutorecoveringConnection) connection);

            for (int i = 0; i < nbMessages; i++) {
                sendMessage(channel2);
            }

            waitAtMost(timeout(), () -> ackedMessages.get() == nbMessages * 2);

            assertThat(metrics.getConsumedMessages().getCount()).isEqualTo((long) (nbMessages * 2));
            assertThat(metrics.getAcknowledgedMessages().getCount()).isEqualTo((long) (nbMessages * 2));

        } finally {
            safeClose(connection);
        }
    }

    private static ConnectionFactory createConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(false);
        return connectionFactory;
    }

    private static ConnectionFactory createAutoRecoveryConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        return connectionFactory;
    }

    private void closeAndWaitForRecovery(AutorecoveringConnection connection) throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        connection.addRecoveryListener(new RecoveryListener() {
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }

            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                // no-op
            }
        });
        Host.closeConnection(connection);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private Collection<?> getShutdownHooks(Connection connection) throws NoSuchFieldException, IllegalAccessException {
        Field shutdownHooksField = connection.getClass().getDeclaredField("shutdownHooks");
        shutdownHooksField.setAccessible(true);
        return (Collection<?>) shutdownHooksField.get(connection);
    }

    private static class BasicGetTask implements Callable<Void> {

        final Channel channel;
        final boolean autoAck;
        final Random random = new Random();

        private BasicGetTask(Channel channel, boolean autoAck) {
            this.channel = channel;
            this.autoAck = autoAck;
        }

        @Override
        public Void call() throws Exception {
            GetResponse getResponse = this.channel.basicGet(QUEUE, autoAck);
            if(!autoAck) {
                channel.basicAck(getResponse.getEnvelope().getDeliveryTag(), random.nextBoolean());
            }
            return null;
        }
    }

    private static class BasicConsumeTask implements Callable<Void> {

        final Channel channel;
        final boolean autoAck;
        final Random random = new Random();

        private BasicConsumeTask(Channel channel, boolean autoAck) {
            this.channel = channel;
            this.autoAck = autoAck;
        }

        @Override
        public Void call() throws Exception {
            this.channel.basicConsume(QUEUE, autoAck, new DefaultConsumer(channel) {

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    if(!autoAck) {
                        getChannel().basicAck(envelope.getDeliveryTag(), random.nextBoolean());
                    }
                }
            });
            return null;
        }
    }

    private static class BasicGetRejectTask implements Callable<Void> {

        final Channel channel;
        final Random random = new Random();

        private BasicGetRejectTask(Channel channel) {
            this.channel = channel;
        }

        @Override
        public Void call() throws Exception {
            GetResponse response = channel.basicGet(QUEUE, false);
            if(response != null) {
                if(random.nextBoolean()) {
                    channel.basicNack(response.getEnvelope().getDeliveryTag(), random.nextBoolean(), false);
                } else {
                    channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
                }
            }
            return null;
        }
    }

    private static class BasicConsumeRejectTask implements Callable<Void> {

        final Channel channel;
        final Random random = new Random();

        private BasicConsumeRejectTask(Channel channel) {
            this.channel = channel;
        }

        @Override
        public Void call() throws Exception {
            this.channel.basicConsume(QUEUE, false, new DefaultConsumer(channel) {

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    if(random.nextBoolean()) {
                        channel.basicNack(envelope.getDeliveryTag(), random.nextBoolean(), false);
                    } else {
                        channel.basicReject(envelope.getDeliveryTag(), false);
                    }
                }
            });
            return null;
        }
    }

    private void safeClose(Connection connection) {
        if(connection != null) {
            try {
                connection.abort();
            } catch (Exception e) {
                // OK
            }
        }
    }

    private void sendMessage(Channel channel) throws IOException {
        channel.basicPublish("", QUEUE, null, "msg".getBytes("UTF-8"));
    }

    private Duration timeout() {
        return Duration.ofSeconds(10);
    }

    private static class MultipleAckConsumer extends DefaultConsumer {

        final boolean multiple;
        private AtomicInteger ackedCount = new AtomicInteger(0);

        public MultipleAckConsumer(Channel channel, boolean multiple) {
            super(channel);
            this.multiple = multiple;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            try {
                Thread.sleep(new Random().nextInt(10));
            } catch (InterruptedException e) {
                throw new RuntimeException("Error during randomized wait",e);
            }
            getChannel().basicAck(envelope.getDeliveryTag(), multiple);
            ackedCount.incrementAndGet();
        }

        int ackedCount() {
            return this.ackedCount.get();
        }
    }

}
