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

import java.io.IOException;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

/* Publish a message of size FRAME_MAX.  The broker should split this
 * into two frames before sending back. */
public class FrameMax extends BrokerTestCase {
    /* This value for FrameMax is larger than the minimum and less
     * than what Rabbit suggests. */
    final static int FRAME_MAX = 131008;
    final static int TIMEOUT = 3000; /* Time to wait for messages. */
    final static String EXCHANGE_NAME = "xchg1";
    final static String ROUTING_KEY = "something";

    QueueingConsumer consumer;

    @Override
    protected void setUp()
        throws IOException
    {
        super.setUp();
        connectionFactory.setRequestedFrameMax(FRAME_MAX);
    }

    @Override
    protected void createResources()
        throws IOException
    {
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        consumer = new QueueingConsumer(channel);
        String queueName = channel.queueDeclare().getQueue();
        channel.basicConsume(queueName, consumer);
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
    }

    @Override
    protected void releaseResources()
        throws IOException
    {
        consumer = null;
        channel.exchangeDelete(EXCHANGE_NAME);
    }

    /* Frame content should be less or equal to frame-max - 8. */
    public void testFrameSizes()
        throws IOException, InterruptedException
    {
        int howMuch = FRAME_MAX;
        produce(howMuch);
        /* Receive everything that was sent out. */
        while (howMuch > 0) {
            Delivery delivery = consumer.nextDelivery(TIMEOUT);
            int received = delivery.getBody().length;
            assertTrue(received <= FRAME_MAX - 8);
            howMuch -= received;
        }
    }

    /* Send out howMuch worth of gibberish */
    protected void produce(int howMuch)
        throws IOException
    {
        while (howMuch > 0) {
            int size = (howMuch <= (FRAME_MAX-8)) ? howMuch : (FRAME_MAX-8);
            publish(new byte[size]);
            howMuch -= (FRAME_MAX-8);
        }
    }

    /* Publish a non-persistant, non-immediate message. */
    private void publish(byte[] msg)
        throws IOException
    {
        channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY,
                             false, false,
                             MessageProperties.MINIMAL_BASIC,
                             msg);
    }
}
