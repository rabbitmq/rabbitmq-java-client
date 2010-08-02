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

import com.rabbitmq.client.test.BrokerTestCase;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

import java.io.IOException;
import java.util.Arrays;

public class Reject extends BrokerTestCase
{

    protected Channel secondaryChannel;

    @Override
    protected void setUp()
        throws IOException
    {
        super.setUp();
        secondaryChannel = connection.createChannel();

    }

    @Override
    protected void tearDown()
        throws IOException
    {
        if (secondaryChannel != null) {
            secondaryChannel.abort();
            secondaryChannel = null;
        }
        super.tearDown();
    }

    protected long checkDelivery(Delivery d, byte[] msg, boolean redelivered)
    {
        assertNotNull(d);
        return checkDelivery(d.getEnvelope(), d.getBody(), msg, redelivered);
    }

    protected long checkDelivery(GetResponse r, byte[] msg, boolean redelivered)
    {
        assertNotNull(r);
        return checkDelivery(r.getEnvelope(), r.getBody(), msg, redelivered);
    }

    protected long checkDelivery(Envelope e, byte[] m,
                                 byte[] msg, boolean redelivered)
    {
        assertNotNull(e);
        assertTrue(Arrays.equals(m, msg));
        assertEquals(e.isRedeliver(), redelivered);
        return e.getDeliveryTag();
    }

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
        expectChannelError(AMQP.PRECONDITION_FAILED);
    }
}
