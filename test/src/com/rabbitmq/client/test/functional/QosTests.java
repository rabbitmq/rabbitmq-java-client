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
import java.util.List;
import java.util.ArrayList;

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
                                 Integer.toString(n).getBytes());
	}
    }

    /**
     * receive n messages - check that we receive no fewer and cannot
     * receive more
     **/
    public void drain(QueueingConsumer c, int n)
	throws IOException
    {
	try {
            Thread.sleep(100);
            assertEquals(n, c.getQueue().size());
	} catch (InterruptedException ie) {
	    fail("interrupted");
	}
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

    public void testMessageLimitUnlimited()
	throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
	configure(c, 0, 1, 2);
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

    protected void runLimitTests(int limit,
                                 boolean multiAck,
                                 boolean txMode,
                                 int queueCount)
        throws IOException
    {
        try {
            runLimitTestsHelper(limit, multiAck, txMode, queueCount);
        } catch (InterruptedException e) {
            fail("interrupted");
        }
    }

    protected void runLimitTestsHelper(int limit,
                                       boolean multiAck,
                                       boolean txMode,
                                       int queueCount)
        throws IOException, InterruptedException
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
        drain(c, limit);

        //is basic.get not limited?
        List<Long> tags = new ArrayList<Long>();
        for (String q : queues) {
            GetResponse r = channel.basicGet(q, false);
            assertNotNull(r);
            tags.add(r.getEnvelope().getDeliveryTag());
        }

        //are acks handled correctly?
        //and does the basic.get above have no effect on limiting?
        Delivery last = ack(c, multiAck);
        if (txMode) {
            drain(c, 0);
            channel.txRollback();
            drain(c, 0);
            ackDelivery(last, true);
            channel.txCommit();
        }
        drain(c, limit);

        //do acks for basic.gets have no effect on limiting?
        for (int i = 0; i < limit; i++) {
            c.nextDelivery();
        }
        for (long t  : tags) {
            channel.basicAck(t, false);
        }
        if (txMode) {
            channel.txCommit();
        }
        drain(c, 0);
    }

    protected Delivery ack(QueueingConsumer c, boolean multiAck)
        throws IOException, InterruptedException
    {
        Delivery last = null;
        if (multiAck) {
            for (Delivery tmp = null; (tmp = c.nextDelivery(0)) != null; last = tmp);
            ackDelivery(last, true);
        } else {
            for (Delivery tmp = null; (tmp = c.nextDelivery(0)) != null; last = tmp) {
                ackDelivery(tmp, false);
            }
        }
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
            AMQP.Queue.DeclareOk ok = channel.queueDeclare();
            String queue = ok.getQueue();
            queues.add(queue);
            channel.queueBind(queue, "amq.fanout", "");
            channel.basicConsume(queue, false, c);
        }

        //publish
        fill(messages);

        return queues;
    }

    protected void ackDelivery(Delivery d, boolean multiple)
        throws IOException
    {
        channel.basicAck(d.getEnvelope().getDeliveryTag(), multiple);
    }

}
