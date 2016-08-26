// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test.functional;

import static org.junit.Assert.*;
import org.junit.Test;


import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

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

    protected abstract void open() throws IOException, TimeoutException;

    protected abstract void close() throws IOException;

    public void setUp()
        throws IOException
    {
        // Override to disable the default behaviour from BrokerTestCase.
    }

    public void tearDown()
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
            throws IOException, InterruptedException, TimeoutException {
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
    @Test public void normal() throws Exception
    {
        publishAndGet(3, true);
    }

    /**
     * Test we requeue unacknowledged messages (using get)
     * @throws Exception untested
     */
    @Test public void requeueing() throws Exception
    {
        publishAndGet(3, false);
    }

    /**
     * Test we requeue unacknowledged message (using consumer)
     * @throws Exception untested
     */
    @Test public void requeueingConsumer() throws Exception
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
            throws IOException, InterruptedException, ShutdownSignalException, TimeoutException {
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
    @Test public void requeueInFlight() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndGet();
        }
    }

    /**
     * Test close while consuming partially not acked with cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    @Test public void requeueInFlightConsumerNoAck() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(false, true);
        }
    }

    /**
     * Test close while consuming partially acked with cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    @Test public void requeueInFlightConsumerAck() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(true, true);
        }
    }

    /**
     * Test close while consuming partially not acked without cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    @Test public void requeueInFlightConsumerNoAckNoCancel() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(false, false);
        }
    }

    /**
     * Test close while consuming partially acked without cancel successfully requeues unacknowledged messages
     * @throws Exception untested
     */
    @Test public void requeueInFlightConsumerAckNoCancel() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            publishLotsAndConsumeSome(true, false);
        }
    }

    private static final int MESSAGES_TO_CONSUME = 20;

    private void publishLotsAndConsumeSome(boolean ack, boolean cancelBeforeFinish)
            throws IOException, InterruptedException, ShutdownSignalException, TimeoutException {
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
        private final Channel channel;
        private final CountDownLatch latch;
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
