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

import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
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
            open();
            if (doAck) {
                assertNull("Expected missing second basicGet (repeat="+repeat+")", getMessage());
            } else {
                assertNotNull("Expected present second basicGet (repeat="+repeat+")", getMessage());
            }
            close();
        }
        closeConnection();
    }

    /**
     * Test we don't requeue acknowledged messages (using get)
     * @throws Exception untested
     */
    public void testNormal() throws Exception
    {
        publishAndGet(3, true);
    }

    /**
     * Test we requeue unacknowledged messages (using get)
     * @throws Exception untested
     */
    public void testRequeueing() throws Exception
    {
        publishAndGet(3, false);
    }

    /**
     * Test we requeue unacknowledged message (using consumer)
     * @throws Exception untested
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
            assertNotNull("only got " + i + " out of " + MESSAGE_COUNT +
                          " messages", channel.basicGet(Q, true));
        }
        assertNull("got more messages than " + MESSAGE_COUNT + " expected", channel.basicGet(Q, true));
        channel.queueDelete(Q);
        close();
        closeConnection();
    }

    /**
     * Test close while consuming many messages successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    public void testRequeueInFlight() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndGet();
        }
    }

    /**
     * Test close while consuming partially not acked with cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    public void testRequeueInFlightConsumerNoAck() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(false, true);
        }
    }

    /**
     * Test close while consuming partially acked with cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    public void testRequeueInFlightConsumerAck() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(true, true);
        }
    }

    /**
     * Test close while consuming partially not acked without cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    public void testRequeueInFlightConsumerNoAckNoCancel() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(false, false);
        }
    }

    /**
     * Test close while consuming partially acked without cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    public void testRequeueInFlightConsumerAckNoCancel() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(true, false);
        }
    }

    private static final int MESSAGES_TO_CONSUME = 20;

    private void publishLotsAndConsumeSome(boolean ack, boolean cancelBeforeFinish)
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

        CountDownLatch latch = new CountDownLatch(1);
        PartialConsumer c = new PartialConsumer(channel, MESSAGES_TO_CONSUME, ack, latch, cancelBeforeFinish);
        channel.basicConsume(Q, c);
        latch.await();  // wait for consumer

        close();
        open();
        int requeuedMsgCount = (ack) ? MESSAGE_COUNT - MESSAGES_TO_CONSUME : MESSAGE_COUNT;
        for (int i = 0; i < requeuedMsgCount; i++) {
            assertNotNull("only got " + i + " out of " + requeuedMsgCount + " messages",
                    channel.basicGet(Q, true));
        }
        int countMoreMsgs = 0;
        while (null != channel.basicGet(Q, true)) {
            countMoreMsgs++;
        }
        assertTrue("got " + countMoreMsgs + " more messages than " + requeuedMsgCount + " expected", 0==countMoreMsgs);
        channel.queueDelete(Q);
        close();
        closeConnection();
    }

    private class PartialConsumer extends DefaultConsumer {

        private volatile int count;
        private Channel channel;
        private CountDownLatch latch;
        private volatile boolean acknowledge;
        private final boolean cancelBeforeFinish;

        public PartialConsumer(Channel channel, int count, boolean acknowledge, CountDownLatch latch, boolean cancelBeforeFinish) {
            super(channel);
            this.count = count;
            this.channel = channel;
            this.latch = latch;
            this.acknowledge = acknowledge;
            this.cancelBeforeFinish = cancelBeforeFinish;
        }

        @Override
        public void handleDelivery(String consumerTag,
                Envelope envelope,
                AMQP.BasicProperties properties,
                byte[] body)
        throws IOException
        {
            if (this.acknowledge)
                this.channel.basicAck(envelope.getDeliveryTag(), false);
            if (--this.count == 0) {
                if (this.cancelBeforeFinish)
                    this.channel.basicCancel(this.getConsumerTag());
                this.acknowledge = false; // don't acknowledge any more
                this.latch.countDown();
            }
        }
    }
}
