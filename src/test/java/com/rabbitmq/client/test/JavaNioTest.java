package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class JavaNioTest {

    @Test
    public void connection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
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
        connectionFactory.useNio();
        connectionFactory.setNioParams(new NioParams().setNbIoThreads(4));
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

    @Test
    public void twoConnectionsWithNioExecutor() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService nioExecutor = Executors.newFixedThreadPool(5);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.setNioParams(new NioParams().setNioExecutor(nioExecutor));
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
            nioExecutor.shutdownNow();
        }
    }

    @Test
    public void shutdownListenerCalled() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        Connection connection = connectionFactory.newConnection();
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            connection.addShutdownListener(new ShutdownListener() {

                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    latch.countDown();
                }
            });
            safeClose(connection);
            assertTrue("Shutdown listener should have been called", latch.await(5, TimeUnit.SECONDS));
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void nioLoopCleaning() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        for(int i = 0; i < 10; i++) {
            Connection connection = connectionFactory.newConnection();
            connection.abort();
        }
    }

    private Connection basicGetBasicConsume(ConnectionFactory connectionFactory, String queue, final CountDownLatch latch)
        throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, new byte[20000]);

        channel.basicConsume(queue, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                latch.countDown();
            }
        });

        return connection;
    }

    private void safeClose(Connection connection) {
        if (connection != null) {
            try {
                connection.abort();
            } catch (Exception e) {
                // OK
            }
        }
    }
}
