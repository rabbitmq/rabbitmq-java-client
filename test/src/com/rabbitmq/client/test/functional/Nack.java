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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
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

         checkDelivery(c.nextDelivery(), m4, true);
         checkDelivery(c.nextDelivery(), m3, true);
         long tag3 = checkDelivery(c.nextDelivery(), m1, true);

         secondaryChannel.basicCancel(consumerTag);

         // no requeue
         secondaryChannel.basicNack(tag3, true, false);

         assertNull(channel.basicGet(q, false));

         channel.basicNack(tag3, true, true);

         expectError(AMQP.PRECONDITION_FAILED);
     }

}
