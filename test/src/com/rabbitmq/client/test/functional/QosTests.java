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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2013 GoPivotal, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.*;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

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

    public void testMessageLimitGlobalFails()
        throws IOException
    {
        try {
            channel.basicQos(0, 1, true);
            fail("basic.qos{global=false} should not be supported");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
        }
    }

    public void testMessageLimitPrefetchSizeFails()
        throws IOException
    {
        try {
            channel.basicQos(1000, 0, false);
            fail("basic.qos{pretfetch_size=NonZero} should not be supported");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
        }
    }

    public void testMessageLimitUnlimited()
        throws IOException
    {
        final Map<String, QosTestConsumer> consumerMap = configure(0, 1, 2);
        final QosTestConsumer consumer =
                consumerMap.entrySet().iterator().next().getValue();
        consumer.maxAvailableDeliveriesShouldBe(2);
    }

    public void testNoAckNoAlterLimit()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(channel, c, true);
        channel.basicQos(1);
        fill(2);
        c.maxAvailableDeliveriesShouldBe(2);
    }

    public void testNoAckObeysLimit()
        throws IOException
    {
        channel.basicQos(1);
        QosTestConsumer c1 = new QosTestConsumer(channel);
        declareBindConsume(channel, c1, false);
        fill(1);
        QosTestConsumer c2 = new QosTestConsumer(channel);
        declareBindConsume(channel, c2, true);
        fill(1);
        try {
            Delivery d = c2.nextDelivery(1000);
            assertNull(d);
        } catch (InterruptedException ie) {
            fail("interrupted");
        }
        Queue<Delivery> d = c1.maxAvailableDeliveriesShouldBe(1);
        ack(d, false); // must ack before the next one appears
        d = c1.maxAvailableDeliveriesShouldBe(1);
        ack(d, false);
        c2.maxAvailableDeliveriesShouldBe(1);
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
        final int queueCount = 3;
        final int messageCount = 100;
        final Map<String, QosTestConsumer> queues = configure(1, queueCount, messageCount);
        final Set<Map.Entry<String, QosTestConsumer>> entrySet = queues.entrySet();
        Iterator<Map.Entry<String, QosTestConsumer>> iterator = entrySet.iterator();

        for (int i = 0; i < messageCount - 1; i++) {
            QosTestConsumer consumer;
            if (iterator.hasNext()) {
                consumer = iterator.next().getValue();
            } else {
                iterator = entrySet.iterator();
                consumer = iterator.next().getValue();
            }

            Queue<Delivery> d = consumer.maxAvailableDeliveriesShouldBe(1);
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
        for (String q : queues.keySet()) {
            AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(q);
            assertTrue(ok.getMessageCount() < messageCount);
        }

    }

    public void testSingleChannelAndQueueFairness()
        throws IOException
    {
        //check that when we have multiple consumers on the same
        //channel & queue, and a prefetch limit set, that all
        //consumers get a fair share of the messages.

        channel.basicQos(1);
        String q = channel.queueDeclare().getQueue();
        channel.queueBind(q, "amq.fanout", "");

        final Map<String, Integer> counts =
            Collections.synchronizedMap(new HashMap<String, Integer>());

        final QueueingConsumer c1 = new CountingQueueingConsumer(counts);
        final QueueingConsumer c2 = new CountingQueueingConsumer(counts);

        channel.basicConsume(q, false, "c1", c1);
        channel.basicConsume(q, false, "c2", c2);

        int count = 10;
        counts.put("c1", 0);
        counts.put("c2", 0);
        fill(count);
        try {
            for (int i = 0; i < count; i++) {
                Delivery d = c1.nextDelivery(500);
                if (d == null) {
                    d = c2.nextDelivery(500);
                }
                channel.basicAck(d.getEnvelope().getDeliveryTag(), false);
            }
        } catch (InterruptedException ie) {
            fail("interrupted");
        }

        //we only check that the server isn't grossly unfair; perfect
        //fairness is too much to ask for (even though RabbitMQ atm
        //does actually provide it in this case)
        assertTrue(counts.get("c1").intValue() > 0);
        assertTrue(counts.get("c2").intValue() > 0);
    }

    public void testConsumerLifecycle()
        throws IOException
    {
        channel.basicQos(1);
        String queue = "qosTest";
        channel.queueDeclare(queue, false, false, false, null);
        channel.queueBind(queue, "amq.fanout", "");
        fill(3);
        String tag;
        for (int i = 0; i < 2; i++) {
            QosTestConsumer c = new QosTestConsumer(channel);
            tag = channel.basicConsume(queue, false, c);
            Queue<Delivery> d = c.maxAvailableDeliveriesShouldBe(1);
            channel.basicCancel(tag);
            c.maxAvailableDeliveriesShouldBe(0);
            ack(d, true);
            c.maxAvailableDeliveriesShouldBe(0);
        }
        channel.queueDelete(queue);
    }

    public void testSetLimitAfterConsume()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(c);
        channel.basicQos(1);
        fill(3);
        //We actually only guarantee that the limit takes effect
        //*eventually*, so this can in fact fail. It's pretty unlikely
        //though.
        Queue<Delivery> d = c.maxAvailableDeliveriesShouldBe(1);
        ack(d, true);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testLimitIncrease()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(2);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testLimitDecrease()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        Queue<Delivery> d = configure(c, 2, 4);
        channel.basicQos(1);
        c.maxAvailableDeliveriesShouldBe(0);
        ack(d, true);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testLimitedToUnlimited()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(0);
        c.maxAvailableDeliveriesShouldBe(2);
    }

    public void testLimitingMultipleChannels()
        throws IOException
    {
        final ChannelWithTwoConsumers channel1 =
                new ChannelWithTwoConsumers(connection.createChannel());
        final ChannelWithTwoConsumers channel2 =
                new ChannelWithTwoConsumers(connection.createChannel());

        channel1.getChannel().basicQos(1);
        channel2.getChannel().basicQos(1);
        fill(5);

        Queue<Delivery> d1 = channel1.maxAvailableDeliveriesShouldBe(1);
        Queue<Delivery> d2 = channel2.maxAvailableDeliveriesShouldBe(1);

        ackDelivery(channel1.getChannel(), d1.remove(), true);
        ackDelivery(channel2.getChannel(), d2.remove(), true);
        channel1.maxAvailableDeliveriesShouldBe(1);
        channel2.maxAvailableDeliveriesShouldBe(1);
        channel1.getChannel().close();
        channel2.getChannel().close();
    }

    // wraps a channel and two consumers (for the same queue) - thus we
    // ensure that the maximum available un-acked deliveries for the channel
    // as a whole, match our expectations, without stating specifically to
    // which consumer we expect a delivery to have been routed.
    private static class ChannelWithTwoConsumers {
        private final Channel channel;

        private final QosTestConsumer firstConsumer;
        private final QosTestConsumer secondConsumer;

        private ChannelWithTwoConsumers(Channel channel) throws IOException {
            this.channel = channel;
            firstConsumer = new QosTestConsumer(channel);
            secondConsumer = new QosTestConsumer(channel);
            channel.basicConsume(declareBindConsume(channel, firstConsumer, false),
                                 false, secondConsumer);
        }

        public Channel getChannel() {
            return channel;
        }

        public Queue<Delivery> maxAvailableDeliveriesShouldBe(int n) {
            Queue<Delivery> res = new LinkedList<Delivery>();
            try {
                long start = System.currentTimeMillis();
                for (int i = 0; i < n; i++) {
                    Delivery d = firstConsumer.nextDelivery(1000);
                    if (d == null) {
                        d = secondConsumer.nextDelivery(1000);
                    }
                    assertNotNull(d);
                    res.offer(d);
                }
                long finish = System.currentTimeMillis();
                Thread.sleep( (n == 0 ? 0 : (finish - start) / n) + 10 );
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            return res;
        }
    }

    public void testLimitInheritsUnackedCount()
        throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(c);
        fill(1);
        c.maxAvailableDeliveriesShouldBe(1);
        channel.basicQos(2);
        fill(2);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testFlow() throws IOException
    {
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(c);
        fill(1);
        c.maxAvailableDeliveriesShouldBe(1);
        channel.flow(false);
        fill(1);
        c.maxAvailableDeliveriesShouldBe(0);
        channel.flow(true);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testLimitAndFlow() throws IOException
    {
        channel.basicQos(1);
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(c);
        channel.flow(false);
        fill(3);
        c.maxAvailableDeliveriesShouldBe(0);
        channel.flow(true);
        ack(c.maxAvailableDeliveriesShouldBe(1), false);
        c.maxAvailableDeliveriesShouldBe(1);
        channel.basicQos(0);
        c.maxAvailableDeliveriesShouldBe(1);
    }

    public void testNoConsumers() throws Exception {
        String q = declareBind(channel);
        fill(1);
        channel.flow(false);
        QosTestConsumer c = new QosTestConsumer(channel);
        channel.basicConsume(q, c);
        c.maxAvailableDeliveriesShouldBe(0);
    }

    public void testRecoverReducesLimit() throws Exception {
        channel.basicQos(2);
        QosTestConsumer c = new QosTestConsumer(channel);
        declareBindConsume(c);
        fill(3);
        c.maxAvailableDeliveriesShouldBe(2);
        channel.basicRecover(true);
        c.maxAvailableDeliveriesShouldBe(2);
    }

    protected void runLimitTests(int limit,
                                 boolean multiAck,
                                 boolean txMode,
                                 int queueCount)
        throws IOException
    {

        QosTestConsumer c = new QosTestConsumer(channel);

        // We attempt to drain 'limit' messages twice, do one
        // basic.get per queue, and need one message to spare
        //-> 2*limit + 1*queueCount + 1
        Map<String, QosTestConsumer> queues = configure(limit, queueCount,
                                        2*limit + 1*queueCount + 1);

        if (txMode) {
            channel.txSelect();
        }

        //is limit enforced?
        Queue<Delivery> d = c.maxAvailableDeliveriesShouldBe(limit);

        //is basic.get not limited?
        List<Long> tags = new ArrayList<Long>();
        for (String q : queues.keySet()) {
            GetResponse r = channel.basicGet(q, false);
            assertNotNull(r);
            tags.add(r.getEnvelope().getDeliveryTag());
        }

        //are acks handled correctly?
        //and does the basic.get above have no effect on limiting?
        Delivery last = ack(d, multiAck);
        if (txMode) {
            c.maxAvailableDeliveriesShouldBe(0);
            channel.txRollback();
            c.maxAvailableDeliveriesShouldBe(0);
            ackDelivery(last, true);
            channel.txCommit();
        }
        c.maxAvailableDeliveriesShouldBe(limit);

        //do acks for basic.gets have no effect on limiting?
        for (long t  : tags) {
            channel.basicAck(t, false);
        }
        if (txMode) {
            channel.txCommit();
        }
        c.maxAvailableDeliveriesShouldBe(0);
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

    protected Map<String, QosTestConsumer> configure(int limit,
                                                      int queueCount,
                                                      int messages)
        throws IOException
    {
        channel.basicQos(limit);

        //declare/bind/consume-from queues
        Map<String, QosTestConsumer> queues =
                new HashMap<String, QosTestConsumer>(queueCount);
        for (int i = 0; i < queueCount; i++) {
            final QosTestConsumer queueingConsumer = new QosTestConsumer(channel);
            final String queueName = declareBindConsume(queueingConsumer);
            queues.put(queueName, queueingConsumer);
        }

        //publish
        fill(messages);

        return queues;
    }

    protected Queue<Delivery> configure(QosTestConsumer c,
                                        int limit,
                                        int messages)
        throws IOException
    {
        channel.basicQos(limit);
        declareBindConsume(c);
        fill(messages);
        return c.maxAvailableDeliveriesShouldBe(limit);
    }

    protected String declareBindConsume(QueueingConsumer c)
        throws IOException
    {
        return declareBindConsume(channel, c, false);
    }

    protected static String declareBindConsume(Channel ch,
                                        QueueingConsumer c,
                                        boolean noAck)
        throws IOException
    {
        String queue = declareBind(ch);
        ch.basicConsume(queue, noAck, c);
        return queue;
    }

    protected static String declareBind(Channel ch) throws IOException {
        AMQP.Queue.DeclareOk ok = ch.queueDeclare();
        String queue = ok.getQueue();
        ch.queueBind(queue, "amq.fanout", "");
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

    private static class QosTestConsumer extends QueueingConsumer {

        public QosTestConsumer(Channel ch) {
            super(ch);
        }

        /**
         * receive n messages - check that we receive no fewer and cannot
         * receive more
         **/
        public Queue<Delivery> maxAvailableDeliveriesShouldBe(int n)
                throws IOException
        {
            Queue<Delivery> res = new LinkedList<Delivery>();
            try {
                long start = System.currentTimeMillis();
                for (int i = 0; i < n; i++) {
                    Delivery d = this.nextDelivery(1000);
                    assertNotNull(d);
                    res.offer(d);
                }
                long finish = System.currentTimeMillis();
                Thread.sleep( (n == 0 ? 0 : (finish - start) / n) + 10 );
                assertNull(this.nextDelivery(0));
            } catch (InterruptedException ie) {
                fail("interrupted");
            }
            return res;
        }
    }

    private class CountingQueueingConsumer extends QueueingConsumer {
        private final Map<String, Integer> counts;

        public CountingQueueingConsumer(Map<String, Integer> counts) {
            super(QosTests.this.channel);
            this.counts = counts;
        }

        @Override
        public void handleDelivery(final String consumerTag,
                                   final Envelope envelope,
                                   final AMQP.BasicProperties properties,
                                   final byte[] body) throws IOException {

            counts.put(consumerTag, counts.get(consumerTag) + 1);
            super.handleDelivery(consumerTag, envelope,
                    properties, body);
        }
    }
}
