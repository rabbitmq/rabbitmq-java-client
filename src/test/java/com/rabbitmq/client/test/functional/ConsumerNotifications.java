// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConsumerNotifications extends BrokerTestCase {

    private final String queue = "cancel_notification_queue";

    @Test public void consumerCancellationNotification() throws IOException,
            InterruptedException {
        final BlockingQueue<Boolean> result = new ArrayBlockingQueue<Boolean>(1);

        channel.queueDeclare(queue, false, true, false, null);
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleCancel(String consumerTag) {
                try {
                    result.put(true);
                } catch (InterruptedException e) {
                    fail();
                }
            }
        };
        channel.basicConsume(queue, consumer);
        channel.queueDelete(queue);
        assertTrue(result.take());
    }

    @Test public void consumerCancellationHandlerUsesBlockingOperations()
            throws IOException, InterruptedException {
        final String altQueue = "basic.cancel.fallback";
        channel.queueDeclare(queue, false, true, false, null);

        CountDownLatch latch = new CountDownLatch(1);
        final AlteringConsumer consumer = new AlteringConsumer(channel, altQueue, latch);

        channel.basicConsume(queue, consumer);
        channel.queueDelete(queue);

        latch.await(2, TimeUnit.SECONDS);
    }

    @Test
    void handleShutdownShouldBeCalledWhenChannelIsClosed() throws Exception {
        Channel ch = connection.createChannel();
        String q = ch.queueDeclare().getQueue();
        CountDownLatch latch = new CountDownLatch(1);
        ch.basicConsume(q, (ctag, msg) -> {}, (ctag, r) -> latch.countDown());
        ch.close();
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static class AlteringConsumer extends DefaultConsumer {
        private final String altQueue;
        private final CountDownLatch latch;

        public AlteringConsumer(Channel channel, String altQueue, CountDownLatch latch) {
            super(channel);
            this.altQueue = altQueue;
            this.latch = latch;
        }

        @Override
        public void handleShutdownSignal(String consumerTag,
                                         ShutdownSignalException sig) {
            // no-op
        }

        @Override
        public void handleCancel(String consumerTag) {
            try {
                this.getChannel().queueDeclare(this.altQueue, false, true, false, null);
                latch.countDown();
            } catch (IOException e) {
                // e.printStackTrace();
            }
        }
    }
}
