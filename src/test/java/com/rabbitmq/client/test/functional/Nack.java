// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.TestUtils.CallableFunction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import java.util.UUID;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class Nack extends AbstractRejectTest {

    public static Object[] queueCreators() {
        return new Object[] {
            (CallableFunction<Channel, String>) channel -> {
                String q = UUID.randomUUID().toString();
                channel.queueDeclare(q, true, false, false, Collections.singletonMap("x-queue-type", "quorum"));
                return q;
            },
            (CallableFunction<Channel, String>) channel -> {
                String q = UUID.randomUUID().toString();
                channel.queueDeclare(q, true, false, false, Collections.singletonMap("x-queue-type", "classic"));
                return q;
            }};
        }

    @ParameterizedTest
    @MethodSource("queueCreators")
    public void singleNack(TestUtils.CallableFunction<Channel, String> queueCreator) throws Exception {
        String q = queueCreator.apply(channel);

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        channel.confirmSelect();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

        channel.waitForConfirmsOrDie(1000);

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

    @ParameterizedTest
    @MethodSource("queueCreators")
    public void multiNack(TestUtils.CallableFunction<Channel, String> queueCreator) throws Exception {
        String q = queueCreator.apply(channel);

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();
        byte[] m3 = "3".getBytes();
        byte[] m4 = "4".getBytes();

        channel.confirmSelect();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);
        basicPublishVolatile(m3, q);
        basicPublishVolatile(m4, q);

        channel.waitForConfirmsOrDie(1000);

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

    @ParameterizedTest
    @MethodSource("queueCreators")
    public void nackAll(TestUtils.CallableFunction<Channel, String> queueCreator) throws Exception {
        String q = queueCreator.apply(channel);

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        channel.confirmSelect();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

        channel.waitForConfirmsOrDie(1000);

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
            assertTrue(msgSet.remove(m), "Unexpected message");
            checkDelivery(delivery, m.getBytes(), true);
            lastTag = delivery.getEnvelope().getDeliveryTag();
        }

        assertTrue(msgSet.isEmpty());
        return lastTag;
    }
}
