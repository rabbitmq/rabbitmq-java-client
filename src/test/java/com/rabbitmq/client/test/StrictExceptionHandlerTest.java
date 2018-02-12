// Copyright (c) 2018-Present Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.StrictExceptionHandler;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class StrictExceptionHandlerTest {

    @Test
    public void tooLongClosingMessage() throws Exception {
        ConnectionFactory cf = TestUtils.connectionFactory();
        final CountDownLatch latch = new CountDownLatch(1);
        cf.setExceptionHandler(new StrictExceptionHandler() {

            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {
                try {
                    super.handleConsumerException(channel, exception, consumer, consumerTag, methodName);
                } catch (IllegalArgumentException e) {
                    fail("No exception should caught");
                }
                latch.countDown();
            }
        });
        try (Connection c = cf.newConnection()) {
            Channel channel = c.createChannel();
            String queue = channel.queueDeclare().getQueue();
            channel.basicConsume(queue,
                new VeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongClassName(
                    channel
                ));
            channel.basicPublish("", queue, null, new byte[0]);
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        }
    }

    static class VeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongClassName
        extends DefaultConsumer {

        public VeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongClassName(
            Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
            throw new RuntimeException();
        }
    }
}
