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
//  Copyright (c) 2007-2013 GoPivotal, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class ConsumerCancelNotificiation extends BrokerTestCase {

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


    class NeverSayDieConsumer extends DefaultConsumer {
        private final String altQueue;

        /**
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        public NeverSayDieConsumer(Channel channel, String altQueue) {
            super(channel);
            this.altQueue = altQueue;
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
            } catch (IOException e) {
                // e.printStackTrace();
            }
        }
    }

    public void testConsumerCancellationHandlerUsesBlockingOperations()
            throws IOException, InterruptedException {
        final String altQueue = "basic.cancel.fallback";
        channel.queueDeclare(queue, false, true, false, null);

        final NeverSayDieConsumer consumer = new NeverSayDieConsumer(channel, altQueue);

        channel.basicConsume(queue, consumer);
        channel.queueDelete(queue);

        Thread.sleep(500);
        channel.queueDeclarePassive(altQueue);
    }
}
