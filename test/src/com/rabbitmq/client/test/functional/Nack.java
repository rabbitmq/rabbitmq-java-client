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

import java.util.HashSet;
import java.util.Set;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;

public class Nack extends AbstractRejectTest {

    public void testSingleNack() throws Exception {
        String q =
            channel.queueDeclare("", false, true, false, null).getQueue();

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

        long tag1 = checkDelivery(channel.basicGet(q, false), m1, false);
        long tag2 = checkDelivery(channel.basicGet(q, false), m2, false);

        QueueingConsumer c = new QueueingConsumer(secondaryChannel);
        String consumerTag = secondaryChannel.basicConsume(q, false, c);

        // requeue
        channel.basicNack(tag2, false, true);

        long tag3 = checkDelivery(c.nextDelivery(), m2, true);
        secondaryChannel.basicCancel(consumerTag);

        // no requeue
        secondaryChannel.basicNack(tag3, false, false);

        assertNull(channel.basicGet(q, false));
        channel.basicAck(tag1, false);
        channel.basicNack(tag3, false, true);

        expectError(AMQP.PRECONDITION_FAILED);
    }

    public void testMultiNack() throws Exception {
        String q =
            channel.queueDeclare("", false, true, false, null).getQueue();

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();
        byte[] m3 = "3".getBytes();
        byte[] m4 = "4".getBytes();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);
        basicPublishVolatile(m3, q);
        basicPublishVolatile(m4, q);

        checkDelivery(channel.basicGet(q, false), m1, false);
        long tag1 = checkDelivery(channel.basicGet(q, false), m2, false);
        checkDelivery(channel.basicGet(q, false), m3, false);
        long tag2 = checkDelivery(channel.basicGet(q, false), m4, false);

        // ack, leaving a gap in un-acked sequence
        channel.basicAck(tag1, false);

        QueueingConsumer c = new QueueingConsumer(secondaryChannel);
        String consumerTag = secondaryChannel.basicConsume(q, false, c);

        // requeue multi
        channel.basicNack(tag2, true, true);

        long tag3 = checkDeliveries(c, m1, m3, m4);

        secondaryChannel.basicCancel(consumerTag);

        // no requeue
        secondaryChannel.basicNack(tag3, true, false);

        assertNull(channel.basicGet(q, false));

        channel.basicNack(tag3, true, true);

        expectError(AMQP.PRECONDITION_FAILED);
    }

    public void testNackAll() throws Exception {
        String q =
            channel.queueDeclare("", false, true, false, null).getQueue();

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

        checkDelivery(channel.basicGet(q, false), m1, false);
        checkDelivery(channel.basicGet(q, false), m2, false);

        // nack all
        channel.basicNack(0, true, true);

        QueueingConsumer c = new QueueingConsumer(secondaryChannel);
        String consumerTag = secondaryChannel.basicConsume(q, true, c);

        checkDeliveries(c, m1, m2);

        secondaryChannel.basicCancel(consumerTag);
    }

    private long checkDeliveries(QueueingConsumer c, byte[]... messages)
            throws InterruptedException {

        Set<String> msgSet = new HashSet<String>();
        for (byte[] message : messages) {
            msgSet.add(new String(message));
        }

        long lastTag = -1;
        for(int x = 0; x < messages.length; x++) {
            QueueingConsumer.Delivery delivery = c.nextDelivery();
            String m = new String(delivery.getBody());
            assertTrue("Unexpected message", msgSet.remove(m));
            checkDelivery(delivery, m.getBytes(), true);
            lastTag = delivery.getEnvelope().getDeliveryTag();
        }

        assertTrue(msgSet.isEmpty());
        return lastTag;
    }
}
