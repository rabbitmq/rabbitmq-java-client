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
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.ShutdownSignalException;

import com.rabbitmq.client.AMQP;

public class QosTests extends BrokerTestCase
{

    protected void setUp()
        throws IOException
    {
        openConnection();
        openChannel();
    }

    protected void tearDown()
        throws IOException
    {
        closeChannel();
        closeConnection();
    }

    public void fill(int n)
        throws IOException
    {
        for (int i = 0; i < n; i++) {
            channel.basicPublish("amq.fanout", "", null,
                                 Integer.toString(i).getBytes());
        }
    }

    /**
     * receive n messages - check that we receive no fewer and cannot
     * receive more
     **/
    public Queue<Delivery> drain(QueueingConsumer c, int n)
        throws IOException
    {
        Queue<Delivery> res = new LinkedList<Delivery>();
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                Delivery d = c.nextDelivery(1000);
                assertNotNull(d);
                res.offer(d);
            }
            long finish = System.currentTimeMillis();
            Thread.sleep( (n == 0 ? 0 : (finish - start) / n) + 10 );
            assertNull(c.nextDelivery(0));
        } catch (InterruptedException ie) {
            fail("interrupted");
        }
        return res;
    }

    public void testMessageLimitGlobalFails()
        throws IOException
    {
        try {
            channel.basicQos(0, 1, true);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
        }
    }

    public void testMessageLimitPrefetchSizeFails()
        throws IOException
    {
        try {
            channel.basicQos(1000, 0, false);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
        }
    }

    public void testMessageLimitUnlimited()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 0, 1, 2);
        drain(c, 2);
    }

    public void testNoAckUnlimited()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(channel, c, true);
        channel.basicQos(1);
        fill(2);
        drain(c, 2);
    }

    public void testPermutations()
        throws IOException
    {
        closeChannel();
        for (int limit : Arrays.asList(1, 2)) {
            for (boolean multiAck : Arrays.asList(false, true)) {
                for (boolean txMode : Arrays.asList(true, false)) {
                    for (int queueCount : Arrays.asList(1, 2)) {
                        openChannel();
                        runLimitTests(limit, multiAck, txMode, queueCount);
                        closeChannel();
                    }
                }
            }
        }
    }

    public void testFairness()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        final int queueCount = 3;
        final int messageCount = 10;
        List<String> queues = configure(c, 1, queueCount, messageCount);

        for (int i = 0; i < messageCount - 1; i++) {
            Queue<Delivery> d = drain(c, 1);
            ack(d, false);
        }

        //Perfect fairness would result in every queue having
        //messageCount * (1 - 1 / queueCount) messages left. Perfect
        //unfairness would result  in one queue having no messages
        //left and the other queues having messageCount messages left.
        //
        //We simply check that every queue has had *some* message(s)
        //consumed from it. That ensures that no queue is "left
        //behind" - a notion of fairness somewhat short of perfect but
        //probably good enough.
        for (String q : queues) {
            AMQP.Queue.DeclareOk ok = channel.queueDeclare(q, true, false, true, true, null);
            assertTrue(ok.getMessageCount() < messageCount);
        }
            
    }

    public void testConsumerLifecycle()
        throws IOException
    {
        channel.basicQos(1);
        QueueingConsumer c = new QueueingConsumer(channel);
        String queue = "qosTest";
        channel.queueDeclare(queue, false);
        channel.queueBind(queue, "amq.fanout", "");
        fill(3);
        String tag;
        for (int i = 0; i < 2; i++) {
            tag = channel.basicConsume(queue, false, c);
            Queue<Delivery> d = drain(c, 1);
            channel.basicCancel(tag);
            drain(c, 0);
            ack(d, true);
            drain(c, 0);
        }
        channel.queueDelete(queue);
    }

    public void testSetLimitAfterConsume()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(c);
        channel.basicQos(1);
        fill(3);
        //We actually only guarantee that the limit takes effect
        //*eventually*, so this can in fact fail. It's pretty unlikely
        //though.
        Queue<Delivery> d = drain(c, 1);
        ack(d, true);
        drain(c, 1);
    }

    public void testLimitIncrease()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(2);
        drain(c, 1);
    }

    public void testLimitDecrease()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        Queue<Delivery> d = configure(c, 2, 4);
        channel.basicQos(1);
        drain(c, 0);
        ack(d, true);
        drain(c, 1);
    }

    public void testLimitedToUnlimited()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(0);
        drain(c, 2);
    }

    public void testLimitingMultipleChannels()
        throws IOException
    {
        Channel ch1 = connection.createChannel();
        Channel ch2 = connection.createChannel();
        QueueingConsumer c1 = new QueueingConsumer(ch1);
        QueueingConsumer c2 = new QueueingConsumer(ch2);
        String q1 = declareBindConsume(ch1, c1, false);
        String q2 = declareBindConsume(ch2, c2, false);
        ch1.basicConsume(q2, false, c1);
        ch2.basicConsume(q1, false, c2);
        ch1.basicQos(1);
        ch2.basicQos(1);
        fill(5);
        Queue<Delivery> d1 = drain(c1, 1);
        Queue<Delivery> d2 = drain(c2, 1);
        ackDelivery(ch1, d1.remove(), true);
        ackDelivery(ch2, d2.remove(), true);
        drain(c1, 1);
        drain(c2, 1);
        ch1.close();
        ch2.close();
    }

    protected void runLimitTests(int limit,
                                 boolean multiAck,
                                 boolean txMode,
                                 int queueCount)
        throws IOException
    {

        QueueingConsumer c = new QueueingConsumer(channel);

        // We attempt to drain 'limit' messages twice, do one
        // basic.get per queue, and need one message to spare
        //-> 2*limit + 1*queueCount + 1
        List<String> queues = configure(c, limit, queueCount,
                                        2*limit + 1*queueCount + 1);

        if (txMode) {
            channel.txSelect();
        }

        //is limit enforced?
        Queue<Delivery> d = drain(c, limit);

        //is basic.get not limited?
        List<Long> tags = new ArrayList<Long>();
        for (String q : queues) {
            GetResponse r = channel.basicGet(q, false);
            assertNotNull(r);
            tags.add(r.getEnvelope().getDeliveryTag());
        }

        //are acks handled correctly?
        //and does the basic.get above have no effect on limiting?
        Delivery last = ack(d, multiAck);
        if (txMode) {
            drain(c, 0);
            channel.txRollback();
            drain(c, 0);
            ackDelivery(last, true);
            channel.txCommit();
        }
        drain(c, limit);

        //do acks for basic.gets have no effect on limiting?
        for (long t  : tags) {
            channel.basicAck(t, false);
        }
        if (txMode) {
            channel.txCommit();
        }
        drain(c, 0);
    }

    protected Delivery ack(Queue<Delivery> d, boolean multiAck)
        throws IOException
    {
        Delivery last = null;

        for (Delivery tmp : d) {
            if (!multiAck) ackDelivery(tmp, false);
            last = tmp;
        }
        if (multiAck) ackDelivery(last, true);

        return last;
    }

    protected List<String> configure(QueueingConsumer c,
                                     int limit,
                                     int queueCount,
                                     int messages)
        throws IOException
    {
        channel.basicQos(limit);

        //declare/bind/consume-from queues
        List <String> queues = new ArrayList<String>();
        for (int i = 0; i < queueCount; i++) {
            queues.add(declareBindConsume(c));
        }

        //publish
        fill(messages);

        return queues;
    }

    protected Queue<Delivery> configure(QueueingConsumer c,
                                        int limit,
                                        int messages)
        throws IOException
    {
        channel.basicQos(limit);
        declareBindConsume(c);
        fill(messages);
        return drain(c, limit);
    }

    protected String declareBindConsume(QueueingConsumer c)
        throws IOException
    {
        return declareBindConsume(channel, c, false);
    }

    protected String declareBindConsume(Channel ch,
                                        QueueingConsumer c,
                                        boolean noAck)
        throws IOException
    {
        AMQP.Queue.DeclareOk ok = ch.queueDeclare();
        String queue = ok.getQueue();
        ch.queueBind(queue, "amq.fanout", "");
        ch.basicConsume(queue, noAck, c);
        return queue;
    }

    protected void ackDelivery(Delivery d, boolean multiple)
        throws IOException
    {
        ackDelivery(channel, d, multiple);
    }

    protected void ackDelivery(Channel ch, Delivery d, boolean multiple)
        throws IOException
    {
        ch.basicAck(d.getEnvelope().getDeliveryTag(), multiple);
    }

}
