package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class CountingDownConsumer extends DefaultConsumer {
    private final CountDownLatch latch;

    public CountingDownConsumer(Channel channel, CountDownLatch latch) {
        super(channel);
        this.latch = latch;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        latch.countDown();
    }
}
