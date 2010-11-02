package com.rabbitmq.examples;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

/**
 * @author robharrop
 */
public class PerQueueTTLGetter {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String queue = "ttl.queue";

        // exchange
        GetResponse response = channel.basicGet(queue, false);
        if(response == null) {
            System.out.println("Got no message...");
        } else {
            System.out.println("Got message: " + new String(response.getBody()));
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        }
        channel.close();
        connection.close();
    }
}
