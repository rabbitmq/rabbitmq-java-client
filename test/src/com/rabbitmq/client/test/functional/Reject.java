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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;

public class Reject extends AbstractRejectTest
{
    public void testReject()
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
