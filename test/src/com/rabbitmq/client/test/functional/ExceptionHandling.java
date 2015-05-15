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

    public void testHandleConsumerException() throws IOException, InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        final DefaultExceptionHandler eh = new DefaultExceptionHandler() {
            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {
                latch.countDown();
            }
        };
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
                throw new RuntimeException("oops");
            }
        });
        ch.basicPublish("", q, null, "".getBytes());
        wait(latch);
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
