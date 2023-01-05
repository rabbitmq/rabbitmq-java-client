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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.TestUtils.CallableFunction;
import java.util.Collections;

import java.util.UUID;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class Reject extends AbstractRejectTest
{

    public static Object[] reject() {
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
    @MethodSource
    public void reject(TestUtils.CallableFunction<Channel, String> queueCreator)
        throws Exception
    {
        String q = queueCreator.apply(channel);

        channel.confirmSelect();

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

        channel.waitForConfirmsOrDie(1000);

        long tag1 = checkDelivery(channel.basicGet(q, false), m1, false);
        long tag2 = checkDelivery(channel.basicGet(q, false), m2, false);
        QueueingConsumer c = new QueueingConsumer(secondaryChannel);
        String consumerTag = secondaryChannel.basicConsume(q, false, c);
        channel.basicReject(tag2, true);
        long tag3 = checkDelivery(c.nextDelivery(), m2, true);
        secondaryChannel.basicCancel(consumerTag);
        secondaryChannel.basicReject(tag3, false);
        assertNull(channel.basicGet(q, false));
        channel.basicAck(tag1, false);
        channel.basicReject(tag3, false);
        expectError(AMQP.PRECONDITION_FAILED);
    }
}
