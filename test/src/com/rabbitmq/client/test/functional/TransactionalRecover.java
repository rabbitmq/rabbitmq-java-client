package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * Test basic.recover on a transactional channel
 */
public class TransactionalRecover extends BrokerTestCase {
    byte[] body = "Hello!".getBytes();

    public void createResources() throws IOException {
    }

    public void testRedeliverAckedUncommitted() throws IOException, InterruptedException {
        String queue = channel.queueDeclare().getQueue();
        channel.txSelect();
        channel.basicPublish("", queue, null, body);
        channel.txCommit();
        GetResponse response = channel.basicGet(queue, false);

        // Ack the message but do not commit the channel. The message should
        // not get redelivered (see
        // https://bugzilla.rabbitmq.com/show_bug.cgi?id=21845#c3)

        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        channel.basicRecover(true);

        assertNull("Acked uncommitted message redelivered",
                channel.basicGet(queue, true));
    }
}
