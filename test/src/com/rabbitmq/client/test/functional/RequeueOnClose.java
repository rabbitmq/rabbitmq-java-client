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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

public abstract class RequeueOnClose
    extends BrokerTestCase
{
    public static final String Q = "RequeueOnClose";
    public static final int GRATUITOUS_DELAY = 100;
    public static final int MESSAGE_COUNT = 2000;

    protected abstract void open() throws IOException;

    protected abstract void close() throws IOException;

    protected void setUp()
        throws IOException
    {
        // Override to disable the default behaviour from BrokerTestCase.
    }

    protected void tearDown()
        throws IOException
    {
        // Override to disable the default behaviour from BrokerTestCase.
    }

    public void injectMessage()
        throws IOException
    {
        channel.queueDeclare(Q, false, false, false, null);
        channel.queueDelete(Q);
        channel.queueDeclare(Q, false, false, false, null);
        channel.basicPublish("", Q, null, "RequeueOnClose message".getBytes());
    }

    public GetResponse getMessage()
        throws IOException
    {
        return channel.basicGet(Q, false);
    }

    public void publishAndGet(int count, boolean doAck)
        throws IOException, InterruptedException
    {
        openConnection();
        for (int repeat = 0; repeat < count; repeat++) {
            open();
            injectMessage();
            GetResponse r1 = getMessage();
            if (doAck) channel.basicAck(r1.getEnvelope().getDeliveryTag(), false);
            close();
            Thread.sleep(GRATUITOUS_DELAY);
            open();
            GetResponse r2 = getMessage();
            if (doAck && r2 != null) {
                fail("Expected missing second basicGet (repeat="+repeat+")");
            } else if (!doAck && r2 == null) {
                fail("Expected present second basicGet (repeat="+repeat+")");
            }
            close();
        }
        closeConnection();
    }

    public void testNormal()
        throws IOException, InterruptedException
    {
        publishAndGet(3, true);
    }

    public void testRequeueing()
        throws IOException, InterruptedException
    {
        publishAndGet(3, false);
    }

    public void testRequeueingConsumer()
        throws IOException, InterruptedException, ShutdownSignalException
    {
        openConnection();
        open();
        injectMessage();
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(Q, c);
        c.nextDelivery();
        close();
        Thread.sleep(GRATUITOUS_DELAY);
        open();
        assertNotNull(getMessage());
        close();
        closeConnection();
    }

    public void publishLotsAndGet()
        throws IOException, InterruptedException, ShutdownSignalException
    {
        openConnection();
        open();
        channel.queueDeclare(Q, false, false, false, null);
        channel.queueDelete(Q);
        channel.queueDeclare(Q, false, false, false, null);
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            channel.basicPublish("", Q, null, "in flight message".getBytes());
        }
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(Q, c);
        c.nextDelivery();
        close();
        open();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            GetResponse r = channel.basicGet(Q, true);
            assertNotNull("only got " + i + " out of " + MESSAGE_COUNT +
                          " messages", r);
        }
        assertNull(channel.basicGet(Q, true));
        channel.queueDelete(Q);
        close();
        closeConnection();
    }

    public void testRequeueInFlight()
        throws IOException, InterruptedException, ShutdownSignalException
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndGet();
        }
    }

}
