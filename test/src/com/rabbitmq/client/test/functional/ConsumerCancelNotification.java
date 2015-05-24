//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConsumerCancelNotification extends BrokerTestCase {

    private final String queue = "cancel_notification_queue";

    public void testConsumerCancellationNotification() throws IOException,
            InterruptedException {
        final BlockingQueue<Boolean> result = new ArrayBlockingQueue<Boolean>(1);

        channel.queueDeclare(queue, false, true, false, null);
        Consumer consumer = new QueueingConsumer(channel) {
            @Override
            public void handleCancel(String consumerTag) throws IOException {
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

    public void testConsumerCancellationInterruptsQueuingConsumerWait()
            throws IOException, InterruptedException {
        final BlockingQueue<Boolean> result = new ArrayBlockingQueue<Boolean>(1);
        channel.queueDeclare(queue, false, true, false, null);
        final QueueingConsumer consumer = new QueueingConsumer(channel);
        Runnable receiver = new Runnable() {

            public void run() {
                try {
                    try {
                        consumer.nextDelivery();
                    } catch (ConsumerCancelledException e) {
                        result.put(true);
                        return;
                    } catch (ShutdownSignalException e) {
                    } catch (InterruptedException e) {
                    }
                    result.put(false);
                } catch (InterruptedException e) {
                    fail();
                }
            }
        };
        Thread t = new Thread(receiver);
        t.start();
        channel.basicConsume(queue, consumer);
        channel.queueDelete(queue);
        assertTrue(result.take());
        t.join();
    }


    class AlteringConsumer extends DefaultConsumer {
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

    public void testConsumerCancellationHandlerUsesBlockingOperations()
            throws IOException, InterruptedException {
        final String altQueue = "basic.cancel.fallback";
        channel.queueDeclare(queue, false, true, false, null);

        CountDownLatch latch = new CountDownLatch(1);
        final AlteringConsumer consumer = new AlteringConsumer(channel, altQueue, latch);

        channel.basicConsume(queue, consumer);
        channel.queueDelete(queue);

        latch.await(2, TimeUnit.SECONDS);
    }
}
