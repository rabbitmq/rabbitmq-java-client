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

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeExchangeBindings extends BrokerTestCase {

    private static final int TIMEOUT = 5000;
    private static final byte[] MARKER = "MARK".getBytes();

    private final String[] queues = new String[] { "q0", "q1", "q2" };
    private final String[] exchanges = new String[] { "e0", "e1", "e2" };
    private final String[][] bindings = new String[][] { { "q0", "e0" },
                                                         { "q1", "e1" },
                                                         { "q2", "e2" } };

    private QueueingConsumer[] consumers = new QueueingConsumer[] { null, null,
            null };

    protected void publishWithMarker(String x, String rk) throws IOException {
        basicPublishVolatile(x, rk);
        basicPublishVolatile(MARKER, x, rk);
    }

    @Override
    protected void createResources() throws IOException {
        for (String q : queues) {
            channel.queueDeclare(q, false, false, false, null);
        }
        for (String e : exchanges) {
            channel.exchangeDeclare(e, "fanout");
        }
        for (String[] binding : bindings) {
            channel.queueBind(binding[0], binding[1], "");
        }
        for (int idx = 0; idx < consumers.length; ++idx) {
            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(queues[idx], true, consumer);
            consumers[idx] = consumer;
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        for (String q : queues) {
            channel.queueDelete(q);
        }
        for (String e : exchanges) {
            channel.exchangeDelete(e);
        }
    }

    protected void consumeNoDuplicates(QueueingConsumer consumer)
            throws ShutdownSignalException, InterruptedException {
        assertNotNull(consumer.nextDelivery(TIMEOUT));
        Delivery markerDelivery = consumer.nextDelivery(TIMEOUT);
        assertEquals(new String(MARKER), new String(markerDelivery.getBody()));
    }

    public void testBindingCreationDeletion() throws IOException {
        channel.exchangeBind("e2", "e1", "");
        channel.exchangeBind("e2", "e1", "");
        channel.exchangeUnbind("e2", "e1", "");
        try {
            channel.exchangeUnbind("e2", "e1", "");
            fail("expected not_found in testBindingCreationDeletion");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
        }
    }

    /* pre (eN --> qN) for N in [0..2]
     * test (e0 --> q0)
     * add binding (e1 --> e0)
     * test (e1 --> {q1, q0})
     * add binding (e2 --> e1)
     * test (e2 --> {q2, q1, q0})
     */
    public void testSimpleChains() throws IOException, ShutdownSignalException,
            InterruptedException {
        publishWithMarker("e0", "");
        consumeNoDuplicates(consumers[0]);

        channel.exchangeBind("e0", "e1", "");
        publishWithMarker("e1", "");
        consumeNoDuplicates(consumers[0]);
        consumeNoDuplicates(consumers[1]);

        channel.exchangeBind("e1", "e2", "");
        publishWithMarker("e2", "");
        consumeNoDuplicates(consumers[0]);
        consumeNoDuplicates(consumers[1]);
        consumeNoDuplicates(consumers[2]);

        channel.exchangeUnbind("e0", "e1", "");
        channel.exchangeUnbind("e1", "e2", "");
    }

    /* pre (eN --> qN) for N in [0..2]
     * add binding (e0 --> q1)
     * test (e0 --> {q0, q1})
     * add binding (e1 --> e0)
     * resulting in: (e1 --> {q1, e0 --> {q0, q1}})
     * test (e1 --> {q0, q1})
     */
    public void testDuplicateQueueDestinations() throws IOException,
            ShutdownSignalException, InterruptedException {
        channel.queueBind("q1", "e0", "");
        publishWithMarker("e0", "");
        consumeNoDuplicates(consumers[0]);
        consumeNoDuplicates(consumers[1]);

        channel.exchangeBind("e0", "e1", "");

        publishWithMarker("e1", "");
        consumeNoDuplicates(consumers[0]);
        consumeNoDuplicates(consumers[1]);

        channel.exchangeUnbind("e0", "e1", "");
    }

    /* pre (eN --> qN) for N in [0..2]
     * add binding (e1 --> e0)
     * add binding (e2 --> e1)
     * add binding (e0 --> e2)
     * test (eN --> {q0, q1, q2}) for N in [0..2]
     */
    public void testExchangeRoutingLoop() throws IOException,
            ShutdownSignalException, InterruptedException {
        channel.exchangeBind("e0", "e1", "");
        channel.exchangeBind("e1", "e2", "");
        channel.exchangeBind("e2", "e0", "");

        for (String e : exchanges) {
            publishWithMarker(e, "");
            for (QueueingConsumer c : consumers) {
                consumeNoDuplicates(c);
            }
        }

        channel.exchangeUnbind("e0", "e1", "");
        channel.exchangeUnbind("e1", "e2", "");
        channel.exchangeUnbind("e2", "e0", "");
    }

    /* pre (eN --> qN) for N in [0..2]
     * create topic e and bind e --> eN with rk eN for N in [0..2]
     * test publish with rk to e
     * create direct ef and bind e --> ef with rk #
     * bind ef --> eN with rk eN for N in [0..2]
     * test publish with rk to e
     * ( end up with: e -(#)-> ef -(eN)-> eN --> qN;
     *                e -(eN)-> eN for N in [0..2] )
     * Then remove the first set of bindings from e --> eN for N in [0..2]
     * test publish with rk to e
     */
    public void testTopicExchange() throws IOException, ShutdownSignalException,
            InterruptedException {

        channel.exchangeDeclare("e", "topic");

        for (String e : exchanges) {
            channel.exchangeBind(e, "e", e);
        }
        publishAndConsumeAll("e");

        channel.exchangeDeclare("ef", "direct");
        channel.exchangeBind("ef", "e", "#");

        for (String e : exchanges) {
            channel.exchangeBind(e, "ef", e);
        }
        publishAndConsumeAll("e");

        for (String e : exchanges) {
            channel.exchangeUnbind(e, "e", e);
        }
        publishAndConsumeAll("e");

        channel.exchangeDelete("ef");
        channel.exchangeDelete("e");
    }

    protected void publishAndConsumeAll(String exchange)
        throws IOException, ShutdownSignalException, InterruptedException {

        for (String e : exchanges) {
            publishWithMarker(exchange, e);
        }
        for (QueueingConsumer c : consumers) {
            consumeNoDuplicates(c);
        }
    }

}
