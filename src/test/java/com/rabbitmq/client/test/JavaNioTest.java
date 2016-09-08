package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class JavaNioTest {

    @Test
    public void connection() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setNio(true);
        Connection connection = null;
        try {
            connection = basicGetBasicConsume(connectionFactory, "nio.queue", latch);
            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Message has not been received", messagesReceived);
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void twoConnections() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setNio(true);
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = basicGetBasicConsume(connectionFactory, "nio.queue.1", latch);
            connection2 = basicGetBasicConsume(connectionFactory, "nio.queue.2", latch);

            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Messages have not been received", messagesReceived);
        } finally {
            safeClose(connection1);
            safeClose(connection2);
        }
    }

    private Connection basicGetBasicConsume(ConnectionFactory connectionFactory, String queue, final CountDownLatch latch)
        throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, "hello nio world!".getBytes("UTF-8"));

        channel.basicConsume(queue, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                latch.countDown();
            }
        });

        return connection;
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

}
