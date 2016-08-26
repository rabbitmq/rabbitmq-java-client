// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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


package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;

public class Reject extends AbstractRejectTest
{
    @Test public void reject()
        throws IOException, InterruptedException
    {
        String q = channel.queueDeclare("", false, true, false, null).getQueue();

        byte[] m1 = "1".getBytes();
        byte[] m2 = "2".getBytes();

        basicPublishVolatile(m1, q);
        basicPublishVolatile(m2, q);

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
