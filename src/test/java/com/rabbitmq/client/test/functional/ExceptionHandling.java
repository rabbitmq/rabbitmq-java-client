package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import com.rabbitmq.client.impl.ForgivingExceptionHandler;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExceptionHandling extends TestCase {
    private ConnectionFactory newConnectionFactory(ExceptionHandler eh) {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setExceptionHandler(eh);
        return cf;
    }

    public void testDefaultConsumerHandleConsumerException() throws IOException, InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        final ExceptionHandler eh = new DefaultExceptionHandler() {
            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {
                super.handleConsumerException(channel, exception, consumer, consumerTag, methodName);
                latch.countDown();
            }
        };

        testConsumerHandleConsumerException(eh, latch, true);
    }

    public void testForgivingConsumerHandleConsumerException() throws IOException, InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        final ExceptionHandler eh = new ForgivingExceptionHandler() {
            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {
                super.handleConsumerException(channel, exception, consumer, consumerTag, methodName);
                latch.countDown();
            }
        };

        testConsumerHandleConsumerException(eh, latch, false);
    }

    protected void testConsumerHandleConsumerException(ExceptionHandler eh, CountDownLatch latch, boolean expectChannelClose)
            throws InterruptedException, TimeoutException, IOException {
        ConnectionFactory cf = newConnectionFactory(eh);
        assertEquals(cf.getExceptionHandler(), eh);
        Connection conn = cf.newConnection();
        assertEquals(conn.getExceptionHandler(), eh);
        Channel ch = conn.createChannel();
        String q = ch.queueDeclare().getQueue();
        ch.basicConsume(q, new DefaultConsumer(ch) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                throw new RuntimeException("exception expected here, don't freak out");
            }
        });
        ch.basicPublish("", q, null, "".getBytes());
        wait(latch);

        assertEquals(!expectChannelClose, ch.isOpen());
    }

    public void testNullExceptionHandler() {
      ConnectionFactory cf = new ConnectionFactory();
      try {
        cf.setExceptionHandler(null);
        fail("expected setExceptionHandler to throw");
      } catch (IllegalArgumentException iae) {
        // expected
      }
    }

    private void wait(CountDownLatch latch) throws InterruptedException {
        latch.await(1800, TimeUnit.SECONDS);
    }
}
