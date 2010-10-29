package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Collections;

/**
 * @author robharrop
 */
public class PerQueueTTLPublisher {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String exchange = "ttl.exchange";
        String queue = "ttl.queue";

        // exchange
        channel.exchangeDeclare(exchange, "direct");

        // queue
        channel.queueDeclare(queue, true, false, false, Collections.singletonMap("x-message-ttl", (Object) 30000L));
        channel.queueBind(queue, exchange, queue, null);

        // send a message
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        props.setDeliveryMode(2);
        for(int x = 0; x < 10; x++) {
            channel.basicPublish(exchange, queue, props, ("Msg [" + x + "]").getBytes());
        }

        System.out.println("Done");
        channel.close();
        connection.close();
    }
}
