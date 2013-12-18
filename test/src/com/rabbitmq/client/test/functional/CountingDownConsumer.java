package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class CountingDownConsumer implements Consumer {
    private final CountDownLatch latch;

    public CountingDownConsumer(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override
    public void handleConsumeOk(String consumerTag) {
        // no-op
    }

    @Override
    public void handleCancelOk(String consumerTag) {
        // no-op
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        // no-op
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        // no-op
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
        // no-op
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        latch.countDown();
    }
}
