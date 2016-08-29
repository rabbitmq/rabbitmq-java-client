package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.ConcurrentStatistics;
import com.rabbitmq.client.test.BrokerTestCase;
import org.awaitility.Duration;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.awaitility.Awaitility.to;
import static org.awaitility.Awaitility.waitAtMost;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

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
        StatisticsCollector statistics = new ConcurrentStatistics();
        connectionFactory.setStatistics(statistics);
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = connectionFactory.newConnection();
            assertEquals(1, statistics.getConnectionCount());

            connection1.createChannel();
            connection1.createChannel();
            Channel channel = connection1.createChannel();
            assertEquals(3, statistics.getChannelCount());

            sendMessage(channel);
            assertEquals(1, statistics.getPublishedMessageCount());
            sendMessage(channel);
            assertEquals(2, statistics.getPublishedMessageCount());

            GetResponse getResponse = channel.basicGet(QUEUE, true);
            assertEquals(1, statistics.getConsumedMessageCount());
            channel.basicGet(QUEUE, true);
            assertEquals(2, statistics.getConsumedMessageCount());
            channel.basicGet(QUEUE, true);
            assertEquals(2, statistics.getConsumedMessageCount());

            connection2 = connectionFactory.newConnection();
            assertEquals(2, statistics.getConnectionCount());

            connection2.createChannel();
            channel = connection2.createChannel();
            assertEquals(3+2, statistics.getChannelCount());
            sendMessage(channel);
            sendMessage(channel);
            assertEquals(2+2, statistics.getPublishedMessageCount());

            channel.basicGet(QUEUE, true);
            assertEquals(2+1, statistics.getConsumedMessageCount());

            channel.basicConsume(QUEUE, new DefaultConsumer(channel));
            waitAtMost(timeout()).untilCall(to(statistics).getConsumedMessageCount(), equalTo(2L+1L+1L));

            safeClose(connection1);
            waitAtMost(timeout()).untilCall(to(statistics).getConnectionCount(), equalTo(1L));
            waitAtMost(timeout()).untilCall(to(statistics).getChannelCount(), equalTo(2L));

            safeClose(connection2);
            waitAtMost(timeout()).untilCall(to(statistics).getConnectionCount(), equalTo(0L));
            waitAtMost(timeout()).untilCall(to(statistics).getChannelCount(), equalTo(0L));

        } finally {
            safeClose(connection1);
            safeClose(connection2);
        }
    }

    @Test public void statisticsClearStandardConnection() throws IOException, TimeoutException {
        doStatisticsClear(new ConnectionFactory());
    }

    @Test public void statisticsClearAutoRecoveryConnection() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(true);
        doStatisticsClear(connectionFactory);
    }

    private void doStatisticsClear(ConnectionFactory connectionFactory) throws IOException, TimeoutException {
        StatisticsCollector statistics = new ConcurrentStatistics();
        connectionFactory.setStatistics(statistics);
        try {
            Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();
            sendMessage(channel);
            channel.basicGet(QUEUE, true);

            statistics.clear();
            assertEquals(0, statistics.getConnectionCount());
            assertEquals(0, statistics.getChannelCount());
            assertEquals(0, statistics.getPublishedMessageCount());
            assertEquals(0, statistics.getConsumedMessageCount());
        } finally {
            safeClose(connection);
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

}
