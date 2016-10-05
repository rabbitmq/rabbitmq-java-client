// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

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

public class ExceptionHandling {
    private ConnectionFactory newConnectionFactory(ExceptionHandler eh) {
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setExceptionHandler(eh);
        return cf;
    }

    @Test public void defaultConsumerHandleConsumerException() throws IOException, InterruptedException, TimeoutException {
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

    @Test public void forgivingConsumerHandleConsumerException() throws IOException, InterruptedException, TimeoutException {
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

    @Test public void nullExceptionHandler() {
      ConnectionFactory cf = TestUtils.connectionFactory();
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
