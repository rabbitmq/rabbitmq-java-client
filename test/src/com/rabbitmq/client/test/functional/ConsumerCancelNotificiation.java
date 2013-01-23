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
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

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
}
