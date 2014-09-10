package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class DirectReplyTo extends BrokerTestCase {
    private static final String QUEUE = "amq.rabbitmq.reply-to";

    public void testRoundTrip() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        declare(connection, replyTo, true);
        channel.confirmSelect();
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);
        channel.waitForConfirms();

        QueueingConsumer.Delivery del = c.nextDelivery();
        assertEquals("response", new String(del.getBody()));
    }

    public void testHack() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        // 5 chars should overwrite part of the key but not the pid; aiming to prove
        // we can't publish using just the pid
        replyTo = replyTo.substring(0, replyTo.length() - 5) + "xxxxx";
        declare(connection, replyTo, false);
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);

        QueueingConsumer.Delivery del = c.nextDelivery(500);
        assertNull(del);
    }

    private void declare(Connection connection, String q, boolean expectedExists) throws IOException {
        Channel ch = connection.createChannel();
        try {
            ch.queueDeclarePassive(q);
            assertTrue(expectedExists);
        } catch (IOException e) {
            assertFalse(expectedExists);
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            // Hmmm...
            channel = connection.createChannel();
        }
    }

    public void testConsumeFail() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        Channel ch = connection.createChannel();
        try {
            ch.basicConsume(QUEUE, false, c);
        } catch (IOException e) {
            // Can't have ack mode
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }

        ch = connection.createChannel();
        ch.basicConsume(QUEUE, true, c);
        try {
            ch.basicConsume(QUEUE, true, c);
        } catch (IOException e) {
            // Can't have multiple consumers
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testConsumeSuccess() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String ctag = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag);

        String ctag2 = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag2);
        assertNotSame(ctag, ctag2);
    }

    private String rpcFirstHalf(QueueingConsumer c) throws IOException {
        channel.basicConsume(QUEUE, true, c);
        String serverQueue = channel.queueDeclare().getQueue();
        basicPublishVolatile("request".getBytes(), "", serverQueue, props());

        GetResponse req = channel.basicGet(serverQueue, true);
        return req.getProps().getReplyTo();
    }

    private AMQP.BasicProperties props() {
        return MessageProperties.BASIC.builder().replyTo(QUEUE).build();
    }
}
