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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Test Requeue of messages on different types of close.
 * Methods {@link #open} and {@link #close} must be implemented by a concrete subclass.
 */
public abstract class RequeueOnClose
    extends BrokerTestCase
{
    private static final String Q = "RequeueOnClose";
    private static final int GRATUITOUS_DELAY = 100;
    private static final int MESSAGE_COUNT = 2000;

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

    private void injectMessage()
        throws IOException
    {
        channel.queueDeclare(Q, false, false, false, null);
        channel.queueDelete(Q);
        channel.queueDeclare(Q, false, false, false, null);
        channel.basicPublish("", Q, null, "RequeueOnClose message".getBytes());
    }

    private GetResponse getMessage()
        throws IOException
    {
        return channel.basicGet(Q, false);
    }

    private void publishAndGet(int count, boolean doAck)
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

    /**
     * Test we don't requeue acknowledged messages (using get)
     * @throws Exception test
     */
    public void testNormal() throws Exception
    {
        publishAndGet(3, true);
    }

    /**
     * Test we requeue unacknowledged messages (using get)
     * @throws Exception test
     */
    public void testRequeueing() throws Exception
    {
        publishAndGet(3, false);
    }

    /**
     * Test we requeue unacknowledged message (using consumer)
     * @throws Exception test
     */
    public void testRequeueingConsumer() throws Exception
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

    private void publishLotsAndGet()
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

    /**
     * Test close while consuming many messages successfully requeues unacknowledged messages
     * @throws Exception test
     */
    public void testRequeueInFlight() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndGet();
        }
    }

}
