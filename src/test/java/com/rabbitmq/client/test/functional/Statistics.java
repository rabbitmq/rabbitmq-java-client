// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.MetricsStatistics;
import com.rabbitmq.client.test.BrokerTestCase;
import org.awaitility.Duration;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.to;
import static org.awaitility.Awaitility.waitAtMost;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class Statistics extends BrokerTestCase {

    static final String QUEUE = "statistics.queue";

    @Override
    protected void createResources() throws IOException, TimeoutException {
        channel.queueDeclare(QUEUE, false, false, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(QUEUE);
    }

    @Test public void statisticsStandardConnection() throws IOException, TimeoutException {
        doStatistics(new ConnectionFactory());
    }

    @Test public void statisticsAutoRecoveryConnection() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        doStatistics(connectionFactory);
    }

    private void doStatistics(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        MetricsStatistics statistics = new MetricsStatistics();
        connectionFactory.setStatistics(statistics);
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = connectionFactory.newConnection();
            assertThat(statistics.getConnections().getCount(), is(1L));

            connection1.createChannel();
            connection1.createChannel();
            Channel channel = connection1.createChannel();
            assertThat(statistics.getChannels().getCount(), is(3L));

            sendMessage(channel);
            assertThat(statistics.getPublishedMessages().getCount(), is(1L));
            sendMessage(channel);
            assertThat(statistics.getPublishedMessages().getCount(), is(2L));

            channel.basicGet(QUEUE, true);
            assertThat(statistics.getConsumedMessages().getCount(), is(1L));
            channel.basicGet(QUEUE, true);
            assertThat(statistics.getConsumedMessages().getCount(), is(2L));
            channel.basicGet(QUEUE, true);
            assertThat(statistics.getConsumedMessages().getCount(), is(2L));

            connection2 = connectionFactory.newConnection();
            assertThat(statistics.getConnections().getCount(), is(2L));

            connection2.createChannel();
            channel = connection2.createChannel();
            assertThat(statistics.getChannels().getCount(), is(3L+2L));
            sendMessage(channel);
            sendMessage(channel);
            assertThat(statistics.getPublishedMessages().getCount(), is(2L+2L));

            channel.basicGet(QUEUE, true);
            assertThat(statistics.getConsumedMessages().getCount(), is(2L+1L));

            channel.basicConsume(QUEUE, true, new DefaultConsumer(channel));
            waitAtMost(timeout()).untilCall(to(statistics.getConsumedMessages()).getCount(), equalTo(2L+1L+1L));

            safeClose(connection1);
            waitAtMost(timeout()).untilCall(to(statistics.getConnections()).getCount(), equalTo(1L));
            waitAtMost(timeout()).untilCall(to(statistics.getChannels()).getCount(), equalTo(2L));

            safeClose(connection2);
            waitAtMost(timeout()).untilCall(to(statistics.getConnections()).getCount(), equalTo(0L));
            waitAtMost(timeout()).untilCall(to(statistics.getChannels()).getCount(), equalTo(0L));

            assertThat(statistics.getAcknowledgedMessages().getCount(), is(0L));
            assertThat(statistics.getRejectedMessages().getCount(), is(0L));

        } finally {
            safeClose(connection1);
            safeClose(connection2);
        }
    }

    @Test public void statisticsAckStandardConnection() throws IOException, TimeoutException {
        doStatisticsAck(new ConnectionFactory());
    }

    @Test public void statisticsAckAutoRecoveryConnection() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        doStatisticsAck(connectionFactory);
    }

    private void doStatisticsAck(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        MetricsStatistics statistics = new MetricsStatistics();
        connectionFactory.setStatistics(statistics);

        try {
            Connection connection = connectionFactory.newConnection();
            Channel channel1 = connection.createChannel();
            Channel channel2 = connection.createChannel();

            sendMessage(channel1);
            GetResponse getResponse = channel1.basicGet(QUEUE, false);
            channel1.basicAck(getResponse.getEnvelope().getDeliveryTag(), false);
            assertThat(statistics.getConsumedMessages().getCount(), is(1L));
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L));

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

            assertThat(statistics.getConsumedMessages().getCount(), is(1L+6L));
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L));

            channel1.basicAck(response5.getEnvelope().getDeliveryTag(), false);
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+1L));
            channel1.basicAck(response3.getEnvelope().getDeliveryTag(), true);
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+1L+2L));

            channel2.basicAck(response2.getEnvelope().getDeliveryTag(), true);
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+(1L+2L)+1L));
            channel2.basicAck(response6.getEnvelope().getDeliveryTag(), true);
            assertThat(statistics.getAcknowledgedMessages().getCount(), is(1L+(1L+2L)+1L+2L));

            long alreadySentMessages = 1+(1+2)+1+2;

            // basicConsume / basicAck
            channel1.basicConsume(QUEUE, false, new MultipleAckConsumer(channel1, false));
            channel1.basicConsume(QUEUE, false, new MultipleAckConsumer(channel1, true));
            channel2.basicConsume(QUEUE, false, new MultipleAckConsumer(channel2, false));
            channel2.basicConsume(QUEUE, false, new MultipleAckConsumer(channel2, true));

            int nbMessages = 10;
            for(int i=0;i<nbMessages;i++) {
                sendMessage(i%2 == 0 ? channel1 : channel2);
            }

            waitAtMost(1, TimeUnit.SECONDS).untilCall(
                to(statistics.getConsumedMessages()).getCount(),
                equalTo(alreadySentMessages+nbMessages)
            );

            waitAtMost(1, TimeUnit.SECONDS).untilCall(
                to(statistics.getAcknowledgedMessages()).getCount(),
                equalTo(alreadySentMessages+nbMessages)
            );

        } finally {
            safeClose(connection);
        }
    }

    @Test public void statisticsRejectStandardConnection() throws IOException, TimeoutException {
        doStatisticsReject(new ConnectionFactory());
    }

    @Test public void statisticsRejectAutoRecoveryConnection() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        doStatisticsReject(connectionFactory);
    }

    private void doStatisticsReject(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        MetricsStatistics statistics = new MetricsStatistics();
        connectionFactory.setStatistics(statistics);

        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        sendMessage(channel);
        sendMessage(channel);
        sendMessage(channel);

        GetResponse response1 = channel.basicGet(QUEUE, false);
        GetResponse response2 = channel.basicGet(QUEUE, false);
        GetResponse response3 = channel.basicGet(QUEUE, false);

        channel.basicReject(response2.getEnvelope().getDeliveryTag(), false);
        assertThat(statistics.getRejectedMessages().getCount(), is(1L));

        channel.basicNack(response3.getEnvelope().getDeliveryTag(), true, false);
        assertThat(statistics.getRejectedMessages().getCount(), is(1L+2L));
    }

    @Test public void multiThreadedStatisticsStandardConnection() throws InterruptedException, TimeoutException, IOException {
        doMultiThreadedStatistics(new ConnectionFactory());
    }

    @Test public void multiThreadedStatisticsAutoRecoveryConnection() throws InterruptedException, TimeoutException, IOException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        doMultiThreadedStatistics(connectionFactory);
    }

    private void doMultiThreadedStatistics(ConnectionFactory connectionFactory) throws IOException, TimeoutException, InterruptedException {
        MetricsStatistics statistics = new MetricsStatistics();
        connectionFactory.setStatistics(statistics);
        int nbConnections = 3;
        int nbChannelsPerConnection = 5;
        int nbChannels = nbConnections * nbChannelsPerConnection;
        long nbOfMessages = 100;
        int nbTasks = nbChannels; // channel are not thread-safe

        Random random = new Random();

        // create connections
        Connection [] connections = new Connection[nbConnections];
        Channel [] channels = new Channel[nbChannels];
        for(int i=0;i<nbConnections;i++) {
            connections[i] = connectionFactory.newConnection();
            for(int j=0;j<nbChannelsPerConnection;j++) {
                Channel channel = connections[i].createChannel();
                channel.basicQos(1);
                channels[i*nbChannelsPerConnection+j] = channel;
            }
        }

        // consume messages without ack
        for(int i=0;i<nbOfMessages;i++) {
            sendMessage(channels[random.nextInt(nbChannels)]);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(nbTasks);
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for(int i=0;i<nbTasks;i++) {
            Channel channelForConsuming = channels[random.nextInt(nbChannels)];
            tasks.add(random.nextInt(10)%2 == 0 ?
                new BasicGetTask(channelForConsuming, true) :
                new BasicConsumeTask(channelForConsuming, true));
        }
        executorService.invokeAll(tasks);

        assertThat(statistics.getPublishedMessages().getCount(), is(nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getConsumedMessages()).getCount(), equalTo(nbOfMessages));
        assertThat(statistics.getAcknowledgedMessages().getCount(), is(0L));

        // to remove the listeners
        for(int i=0;i<nbChannels;i++) {
            channels[i].close();
            Channel channel = connections[random.nextInt(nbConnections)].createChannel();
            channel.basicQos(1);
            channels[i] = channel;
        }

        // consume messages with ack
        for(int i=0;i<nbOfMessages;i++) {
            sendMessage(channels[random.nextInt(nbChannels)]);
        }

        executorService = Executors.newFixedThreadPool(nbTasks);
        tasks = new ArrayList<Callable<Void>>();
        for(int i=0;i<nbTasks;i++) {
            Channel channelForConsuming = channels[i];
            tasks.add(random.nextBoolean() ?
                new BasicGetTask(channelForConsuming, false) :
                new BasicConsumeTask(channelForConsuming, false));
        }
        executorService.invokeAll(tasks);

        assertThat(statistics.getPublishedMessages().getCount(), is(2*nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getConsumedMessages()).getCount(), equalTo(2*nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getAcknowledgedMessages()).getCount(), equalTo(nbOfMessages));

        // to remove the listeners
        for(int i=0;i<nbChannels;i++) {
            channels[i].close();
            Channel channel = connections[random.nextInt(nbConnections)].createChannel();
            channel.basicQos(1);
            channels[i] = channel;
        }

        // consume messages and reject them
        for(int i=0;i<nbOfMessages;i++) {
            sendMessage(channels[random.nextInt(nbChannels)]);
        }

        executorService = Executors.newFixedThreadPool(nbTasks);
        tasks = new ArrayList<Callable<Void>>();
        for(int i=0;i<nbTasks;i++) {
            Channel channelForConsuming = channels[i];
            tasks.add(random.nextBoolean() ?
                new BasicGetRejectTask(channelForConsuming) :
                new BasicConsumeRejectTask(channelForConsuming));
        }
        executorService.invokeAll(tasks);

        assertThat(statistics.getPublishedMessages().getCount(), is(3*nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getConsumedMessages()).getCount(), equalTo(3*nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getAcknowledgedMessages()).getCount(), equalTo(nbOfMessages));
        waitAtMost(1, TimeUnit.SECONDS).untilCall(to(statistics.getRejectedMessages()).getCount(), equalTo(nbOfMessages));
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
        return new Duration(150, TimeUnit.MILLISECONDS);
    }

    private static class MultipleAckConsumer extends DefaultConsumer {

        final boolean multiple;

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
        }
    }

}
