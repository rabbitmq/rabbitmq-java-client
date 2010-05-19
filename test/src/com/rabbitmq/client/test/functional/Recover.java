//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.util.Arrays;
import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.Channel;

import com.rabbitmq.client.test.BrokerTestCase;

public class Recover extends BrokerTestCase {

    String queue;
    byte[] body = "message".getBytes();
    
    public void createResources() throws IOException {
        AMQP.Queue.DeclareOk ok = channel.queueDeclare();
        queue = ok.getQueue();
    }

    static interface RecoverCallback {
        void recover(Channel channel) throws IOException;
        void checkRecover(QueueingConsumer.Delivery delivery, boolean expected) throws IOException;
    }

    // The AMQP specification under-specifies the behaviour when
    // requeue=false.  So we can't really test any scenarios for
    // requeue=false.
  
    void verifyRedeliverOnRecover(RecoverCallback call)
        throws IOException, InterruptedException {
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queue, false, consumer); // require acks.
        channel.basicPublish("", queue, new AMQP.BasicProperties(), body);
        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        call.checkRecover(delivery, false);
        assertTrue("consumed message body not as sent",
                   Arrays.equals(body, delivery.getBody()));
        // Don't ack it, and get it redelivered to the same consumer
        call.recover(channel);
        QueueingConsumer.Delivery secondDelivery = consumer.nextDelivery(5000);
        call.checkRecover(secondDelivery, true);
        assertNotNull("timed out waiting for redelivered message", secondDelivery);
        assertTrue("consumed (redelivered) message body not as sent",
                   Arrays.equals(body, delivery.getBody()));        
    }

    void verifyNoRedeliveryWithAutoAck(RecoverCallback call)
        throws IOException, InterruptedException {
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queue, true, consumer); // auto ack.
        channel.basicPublish("", queue, new AMQP.BasicProperties(), body);
        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        call.checkRecover(delivery, false);
        assertTrue("consumed message body not as sent",
                   Arrays.equals(body, delivery.getBody()));
        call.recover(channel);
        // there's a race here between our recover finishing and the basic.get;
        Thread.sleep(500);
        assertNull("should be no message available", channel.basicGet(queue, true));
    }

    RecoverCallback recoverAsync = new RecoverCallback() {
            public void recover(Channel channel) throws IOException {
                channel.basicRecoverAsync(true);
            }

            public void checkRecover(QueueingConsumer.Delivery delivery, boolean expected) throws IOException {
                // We make no guarantees
            }
        };

    RecoverCallback recoverSync = new RecoverCallback() {
            public void recover(Channel channel) throws IOException {
                channel.basicRecover(true);
            }

            public void checkRecover(QueueingConsumer.Delivery delivery, boolean expected) throws IOException {
                assertEquals(expected, delivery.getRecoverOk());
            }
        };

    public void testRedeliverOnRecoverAsync() throws IOException, InterruptedException {
        verifyRedeliverOnRecover(recoverAsync);
    }
    
    public void testRedeliveryOnRecover() throws IOException, InterruptedException {
        verifyRedeliverOnRecover(recoverSync);
    }

    public void testNoRedeliveryWithAutoAckAsync()
        throws IOException, InterruptedException {
        verifyNoRedeliveryWithAutoAck(recoverAsync);
    }

    public void testNoRedeliveryWithAutoAck()
        throws IOException, InterruptedException {
        verifyNoRedeliveryWithAutoAck(recoverSync);
    }
  
}
