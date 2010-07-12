package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * After a basic.cancel you can invoke basic.recover{requeue=false} and
 * get back delivery(s) with now-obsolete ctags.
 *
 * See bug 22587.
 */
public class RecoverAfterCancel extends BrokerTestCase {
    String queue;

    public void createResources() throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare();
        queue = ok.getQueue();
    }

    public void testRecoverAfterCancel() throws IOException, InterruptedException {
        basicPublishVolatile(queue);
        QueueingConsumer consumer = new QueueingConsumer(channel);
        QueueingConsumer defaultConsumer = new QueueingConsumer(channel);
        channel.setDefaultConsumer(defaultConsumer);
        String ctag = channel.basicConsume(queue, consumer);
        QueueingConsumer.Delivery del = consumer.nextDelivery();
        channel.basicCancel(ctag);
        channel.basicRecover(false);

        // The server will now redeliver us the first message again, with the
        // same ctag, but we're not set up to handle it with a standard
        // consumer - it should end up with the default one.

        QueueingConsumer.Delivery del2 = defaultConsumer.nextDelivery();

        assertEquals(new String(del.getBody()), new String(del2.getBody()));
        assertFalse(del.getEnvelope().isRedeliver());
        assertTrue(del2.getEnvelope().isRedeliver());
    }
}
