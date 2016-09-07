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

    @Test public void connection() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setNio(true);
        Connection connection = connectionFactory.newConnection();
        try {
            Channel channel = connection.createChannel();
            String queue = "nio.queue";
            channel.queueDeclare(queue, false, false, false, null);
            channel.queuePurge(queue);

            channel.basicPublish("", queue, null, "hello nio world!".getBytes("UTF-8"));

            final CountDownLatch latch = new CountDownLatch(1);
            channel.basicConsume(queue, true, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    latch.countDown();
                }
            });

            boolean messageReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Message has not been received", messageReceived);

            channel.close();
        } finally {
            connection.close();
        }

    }

}
