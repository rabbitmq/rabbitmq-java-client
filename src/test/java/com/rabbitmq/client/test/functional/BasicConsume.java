package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class BasicConsume extends BrokerTestCase {

    @Test public void basicConsumeOk() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        basicPublishPersistent("msg".getBytes("UTF-8"), q);
        basicPublishPersistent("msg".getBytes("UTF-8"), q);

        CountDownLatch latch = new CountDownLatch(2);
        channel.basicConsume(q, new CountDownLatchConsumer(channel, latch));

        boolean nbOfExpectedMessagesHasBeenConsumed = latch.await(1, TimeUnit.SECONDS);
        assertTrue(nbOfExpectedMessagesHasBeenConsumed, "Not all the messages have been received");
    }

    @Test public void basicConsumeWithDuplicateTagShouldFailOnClientSide() throws IOException {
        String q = channel.queueDeclare("", false, true, false, null).getQueue();
        String tag = UUID.randomUUID().toString();
        channel.basicConsume(q, false, tag, new DefaultConsumer(channel));
        assertThatThrownBy(() -> channel.basicConsume(q, false, tag, new DefaultConsumer(channel)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(channel.isOpen()).isTrue();
        assertThat(channel.consumerCount(q)).isEqualTo(1);

        channel.basicCancel(tag);
        assertThat(channel.basicConsume(q, false, tag, new DefaultConsumer(channel))).isEqualTo(tag);
        assertThat(channel.consumerCount(q)).isEqualTo(1);
    }

    @Test public void concurrentBasicConsumeWithSameTagShouldRegisterOnlyOneConsumer() throws Exception {
        String q = channel.queueDeclare("", false, true, false, null).getQueue();
        int attempts = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 10; i++) {
                String tag = UUID.randomUUID().toString();
                AtomicInteger successes = new AtomicInteger();
                AtomicInteger rejections = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int j = 0; j < attempts; j++) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        try {
                            channel.basicConsume(q, false, tag, new DefaultConsumer(channel));
                            successes.incrementAndGet();
                        } catch (IllegalStateException e) {
                            rejections.incrementAndGet();
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }
                assertThat(successes.get()).isEqualTo(1);
                assertThat(rejections.get()).isEqualTo(attempts - 1);
                assertThat(channel.isOpen()).isTrue();
                channel.basicCancel(tag);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void basicConsumeWithServerGeneratedTagsShouldNotBeRestricted() throws IOException {
        String q = channel.queueDeclare().getQueue();
        String tag1 = channel.basicConsume(q, false, "", new DefaultConsumer(channel));
        String tag2 = channel.basicConsume(q, false, "", new DefaultConsumer(channel));
        assertThat(tag1).isNotEqualTo(tag2);
        assertThat(channel.consumerCount(q)).isEqualTo(2);
    }

    static class CountDownLatchConsumer extends DefaultConsumer {

        private final CountDownLatch latch;

        public CountDownLatchConsumer(Channel channel, CountDownLatch latch) {
            super(channel);
            this.latch = latch;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            latch.countDown();
        }
    }

}
