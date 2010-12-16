package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class UserIDHeader extends BrokerTestCase {
    public void testValidUserId() throws IOException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties();
        properties.setUserId("guest");
        channel.basicPublish("amq.fanout", "", properties, "".getBytes());
    }

    public void testInvalidUserId() {
        AMQP.BasicProperties properties = new AMQP.BasicProperties();
        properties.setUserId("not the guest, honest");
        try {
            channel.basicPublish("amq.fanout", "", properties, "".getBytes());
            channel.queueDeclare(); // To flush the channel
            fail("Accepted publish with incorrect user ID");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }
}
