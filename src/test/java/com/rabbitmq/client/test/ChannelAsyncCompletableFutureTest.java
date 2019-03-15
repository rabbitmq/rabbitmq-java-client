// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

public class ChannelAsyncCompletableFutureTest extends BrokerTestCase {

    ExecutorService executor;

    String queue;
    String exchange;

    @Override
    protected void createResources() throws IOException, TimeoutException {
        super.createResources();
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        queue = UUID.randomUUID().toString();
        exchange = UUID.randomUUID().toString();
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        executor.shutdownNow();
        channel.queueDelete(queue);
        channel.exchangeDelete(exchange);
    }

    @Test
    public void async() throws Exception {
        channel.confirmSelect();

        CountDownLatch latch = new CountDownLatch(1);
        AMQP.Queue.Declare queueDeclare = new AMQImpl.Queue.Declare.Builder()
            .queue(queue)
            .durable(true)
            .exclusive(false)
            .autoDelete(false)
            .arguments(null)
            .build();

        channel.asyncCompletableRpc(queueDeclare)
            .thenComposeAsync(action -> {
                try {
                    return channel.asyncCompletableRpc(new AMQImpl.Exchange.Declare.Builder()
                        .exchange(exchange)
                        .type("fanout")
                        .durable(false)
                        .autoDelete(false)
                        .arguments(null)
                        .build());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor).thenComposeAsync(action -> {
                try {
                    return channel.asyncCompletableRpc(new AMQImpl.Queue.Bind.Builder()
                        .queue(queue)
                        .exchange(exchange)
                        .routingKey("")
                        .arguments(null)
                        .build());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor).thenAcceptAsync(action -> {
                try {
                    channel.basicPublish("", queue, null, "dummy".getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }, executor).thenAcceptAsync((whatever) -> {
                try {
                    channel.basicConsume(queue, true, new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            latch.countDown();
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }, executor);
        channel.waitForConfirmsOrDie(1000);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

}
