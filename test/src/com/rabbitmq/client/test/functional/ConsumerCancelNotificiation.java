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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

public class ConsumerCancelNotificiation extends BrokerTestCase {

    private final String queue = "cancel_notification_queue";
    
    private final Object lock = new Object();
    
    private boolean notified = false;

    private void assertNotified(boolean expected, boolean wait) {
        synchronized (lock) {
            if (wait) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                }
            }
            assertEquals(notified, expected);
        }
    }
    
    public void testConsumerCancellationNotification() throws IOException {
        channel.queueDeclare(queue, false, true, false, null);
        Consumer consumer = new QueueingConsumer(channel) {
            @Override
            public void handleCancelNotification() throws IOException {
                synchronized (lock) {
                    notified = !notified;
                    lock.notifyAll();
                }
            }
        };
        channel.basicConsume(queue, consumer);
        assertNotified(false, false);
        channel.queueDelete(queue);
        assertNotified(true, true);
    }
}
