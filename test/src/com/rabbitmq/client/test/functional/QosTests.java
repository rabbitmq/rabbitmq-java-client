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

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

import com.rabbitmq.client.AMQP;

public class QosTests extends BrokerTestCase
{
    protected final String Q = "QosTests";

    protected void setUp()
        throws IOException
    {
        openConnection();
        openChannel();
        channel.queueDeclare(Q);
    }

    protected void tearDown()
        throws IOException
    {
	if (channel != null) {
	    channel.queueDelete(Q);
	}
        closeChannel();
        closeConnection();
    }

    public void fill(int n)
	throws IOException
    {
	for (int i = 0; i < n; i++) {
	    channel.basicPublish("", Q, null, Integer.toString(n).getBytes());
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
            Thread.sleep(500);
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
	QueueingConsumer c = publishLimitAndConsume(2, 0);
        drain(c, 2);
    }

    public void testMessageLimit1()
	throws IOException
    {
	runLimitTests(1, false);
    }

    public void testMessageLimit2()
	throws IOException
    {
	runLimitTests(2, false);
    }

    public void testMessageLimitMultiAck()
	throws IOException
    {
	runLimitTests(2, true);
    }

    protected void runLimitTests(int limit, boolean multiAck)
        throws IOException
    {
        try {
            runLimitTestsHelper(limit, multiAck);
        } catch (InterruptedException e) {
            fail("interrupted");
        }
    }

    protected void runLimitTestsHelper(int limit, boolean multiAck)
        throws IOException, InterruptedException
    {

        // We attempt to drain 'limit' messages twice, do one
        // basic.get, and need one message to spare -> 2*limit + 1 + 1
        QueueingConsumer c = publishLimitAndConsume(2*limit + 1 + 1, limit);

        //is limit enforced?
        drain(c, limit);

        //is basic.get not limited?
        GetResponse r = channel.basicGet(Q, false);
        assertNotNull(r);

        //are acks handled correctly?
        //and does the basic.get above have no effect on limiting?
        if (multiAck) {
            for (int i = 0; i < limit - 1; i++) {
                c.nextDelivery();
            }
            channel.basicAck(c.nextDelivery().getEnvelope().getDeliveryTag(),
                             true);
        } else {
            for (int i = 0; i < limit; i++) {
                channel.basicAck(c.nextDelivery().getEnvelope().getDeliveryTag(),
                                 false);
            }
        }
        drain(c, limit);

        //do acks for basic.gets have no effect on limiting?
        for (int i = 0; i < limit; i++) {
            c.nextDelivery();
        }
        channel.basicAck(r.getEnvelope().getDeliveryTag(), false);
        drain(c, 0);
    }

    protected QueueingConsumer publishLimitAndConsume(int messages, int limit)
        throws IOException
    {
	fill(messages);
        channel.basicQos(limit);
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(Q, false, c);
        return c;
    }

}
