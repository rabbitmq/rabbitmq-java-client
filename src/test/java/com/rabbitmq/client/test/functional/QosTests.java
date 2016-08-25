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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.test.BrokerTestCase;

public class QosTests extends BrokerTestCase
{

    public void setUp()
            throws IOException, TimeoutException {
        openConnection();
        openChannel();
    }

    public void tearDown()
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
    public static List<Delivery> drain(QueueingConsumer c, int n)
        throws IOException
    {
        List<Delivery> res = new LinkedList<Delivery>();
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                Delivery d = c.nextDelivery(1000);
                assertNotNull(d);
                res.add(d);
            }
            long finish = System.currentTimeMillis();
            Thread.sleep( (n == 0 ? 0 : (finish - start) / n) + 10 );
            assertNull(c.nextDelivery(0));
        } catch (InterruptedException ie) {
            fail("interrupted");
        }
        return res;
    }

    @Test public void messageLimitPrefetchSizeFails()
        throws IOException
    {
        try {
            channel.basicQos(1000, 0, false);
            fail("basic.qos{pretfetch_size=NonZero} should not be supported");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
        }
    }

    @Test public void messageLimitUnlimited()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 0, 1, 2);
        drain(c, 2);
    }

    @Test public void noAckNoAlterLimit()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(channel, c, true);
        channel.basicQos(1, true);
        fill(2);
        drain(c, 2);
    }

    @Test public void noAckObeysLimit()
        throws IOException
    {
        channel.basicQos(1, true);
        QueueingConsumer c1 = new QueueingConsumer(channel);
        declareBindConsume(channel, c1, false);
        fill(1);
        QueueingConsumer c2 = new QueueingConsumer(channel);
        declareBindConsume(channel, c2, true);
        fill(1);
        try {
            Delivery d = c2.nextDelivery(1000);
            assertNull(d);
        } catch (InterruptedException ie) {
            fail("interrupted");
        }
        List<Delivery> d = drain(c1, 1);
        ack(d, false); // must ack before the next one appears
        d = drain(c1, 1);
        ack(d, false);
        drain(c2, 1);
    }

    @Test public void permutations()
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

    @Test public void fairness()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        final int queueCount = 3;
        final int messageCount = 100;
        List<String> queues = configure(c, 1, queueCount, messageCount);

        for (int i = 0; i < messageCount - 1; i++) {
            List<Delivery> d = drain(c, 1);
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
            AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(q);
            assertTrue(ok.getMessageCount() < messageCount);
        }

    }

    @Test public void singleChannelAndQueueFairness()
        throws IOException
    {
        //check that when we have multiple consumers on the same
        //channel & queue, and a prefetch limit set, that all
        //consumers get a fair share of the messages.

        channel.basicQos(1, true);
        String q = channel.queueDeclare().getQueue();
        channel.queueBind(q, "amq.fanout", "");

        final Map<String, Integer> counts =
            Collections.synchronizedMap(new HashMap<String, Integer>());

        QueueingConsumer c = new QueueingConsumer(channel) {
                @Override public void handleDelivery(String consumerTag,
                                                     Envelope envelope,
                                                     AMQP.BasicProperties properties,
                                                     byte[] body)
                    throws IOException {
                    counts.put(consumerTag, counts.get(consumerTag) + 1);
                    super.handleDelivery(consumerTag, envelope,
                                         properties, body);
                }
            };

        channel.basicConsume(q, false, "c1", c);
        channel.basicConsume(q, false, "c2", c);

        int count = 10;
        counts.put("c1", 0);
        counts.put("c2", 0);
        fill(count);
        try {
            for (int i = 0; i < count; i++) {
                Delivery d = c.nextDelivery();
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

    @Test public void consumerLifecycle()
        throws IOException
    {
        channel.basicQos(1, true);
        QueueingConsumer c = new QueueingConsumer(channel);
        String queue = "qosTest";
        channel.queueDeclare(queue, false, false, false, null);
        channel.queueBind(queue, "amq.fanout", "");
        fill(3);
        String tag;
        for (int i = 0; i < 2; i++) {
            tag = channel.basicConsume(queue, false, c);
            List<Delivery> d = drain(c, 1);
            channel.basicCancel(tag);
            drain(c, 0);
            ack(d, true);
            drain(c, 0);
        }
        channel.queueDelete(queue);
    }

    @Test public void setLimitAfterConsume()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(c);
        channel.basicQos(1, true);
        fill(3);
        //We actually only guarantee that the limit takes effect
        //*eventually*, so this can in fact fail. It's pretty unlikely
        //though.
        List<Delivery> d = drain(c, 1);
        ack(d, true);
        drain(c, 1);
    }

    @Test public void limitIncrease()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(2, true);
        drain(c, 1);
    }

    @Test public void limitDecrease()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        List<Delivery> d = configure(c, 2, 4);
        channel.basicQos(1, true);
        drain(c, 0);
        ack(d, true);
        drain(c, 1);
    }

    @Test public void limitedToUnlimited()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        configure(c, 1, 3);
        channel.basicQos(0, true);
        drain(c, 2);
    }

    @Test public void limitingMultipleChannels()
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
        ch1.basicQos(1, true);
        ch2.basicQos(1, true);
        fill(5);
        List<Delivery> d1 = drain(c1, 1);
        List<Delivery> d2 = drain(c2, 1);
        ackDelivery(ch1, d1.remove(0), true);
        ackDelivery(ch2, d2.remove(0), true);
        drain(c1, 1);
        drain(c2, 1);
        ch1.abort();
        ch2.abort();
    }

    @Test public void limitInheritsUnackedCount()
        throws IOException
    {
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(c);
        fill(1);
        drain(c, 1);
        channel.basicQos(2, true);
        fill(2);
        drain(c, 1);
    }

    @Test public void recoverReducesLimit() throws Exception {
        channel.basicQos(2, true);
        QueueingConsumer c = new QueueingConsumer(channel);
        declareBindConsume(c);
        fill(3);
        drain(c, 2);
        channel.basicRecover(true);
        drain(c, 2);
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
        List<Delivery> d = drain(c, limit);

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

    protected Delivery ack(List<Delivery> d, boolean multiAck)
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
        channel.basicQos(limit, true);

        //declare/bind/consume-from queues
        List <String> queues = new ArrayList<String>();
        for (int i = 0; i < queueCount; i++) {
            queues.add(declareBindConsume(c));
        }

        //publish
        fill(messages);

        return queues;
    }

    protected List<Delivery> configure(QueueingConsumer c,
                                        int limit,
                                        int messages)
        throws IOException
    {
        channel.basicQos(limit, true);
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
        String queue = declareBind(ch);
        ch.basicConsume(queue, noAck, c);
        return queue;
    }

    protected String declareBind(Channel ch) throws IOException {
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

}
