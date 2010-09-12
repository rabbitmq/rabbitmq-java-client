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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeExchangeBindings extends BrokerTestCase {

    private static final int TIMEOUT = 1000;

    private final String[] queues = new String[] { "q0", "q1", "q2" };
    private final String[] exchanges = new String[] { "e0", "e1", "e2" };
    private final String[][] bindings = new String[][] { { "q0", "e0" },
                                                         { "q1", "e1" },
                                                         { "q2", "e2" } };

    private QueueingConsumer[] consumers = new QueueingConsumer[] { null, null,
            null };

    @Override
    protected void setUp() throws IOException {
        super.setUp();
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

    private void consumeExactly(QueueingConsumer consumer, int n)
            throws ShutdownSignalException, InterruptedException {
        for (; n > 0; --n) {
            assertNotNull(consumer.nextDelivery(TIMEOUT));
        }
        assertNull(consumer.nextDelivery(0));
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
        basicPublishVolatile("e0", "");
        consumeExactly(consumers[0], 1);

        channel.exchangeBind("e0", "e1", "");
        basicPublishVolatile("e1", "");
        consumeExactly(consumers[0], 1);
        consumeExactly(consumers[1], 1);

        channel.exchangeBind("e1", "e2", "");
        basicPublishVolatile("e2", "");
        consumeExactly(consumers[0], 1);
        consumeExactly(consumers[1], 1);
        consumeExactly(consumers[2], 1);

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
        basicPublishVolatile("e0", "");
        consumeExactly(consumers[0], 1);
        consumeExactly(consumers[1], 1);

        channel.exchangeBind("e0", "e1", "");

        basicPublishVolatile("e1", "");
        consumeExactly(consumers[0], 1);
        consumeExactly(consumers[1], 1);

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
        channel.exchangeBind("e1", "e0", "");
        channel.exchangeBind("e2", "e1", "");
        channel.exchangeBind("e0", "e2", "");

        for (String e : exchanges) {
            basicPublishVolatile(e, "");
            for (QueueingConsumer c : consumers) {
                consumeExactly(c, 1);
            }
        }

        channel.exchangeUnbind("e1", "e0", "");
        channel.exchangeUnbind("e2", "e1", "");
        channel.exchangeUnbind("e0", "e2", "");
    }
}
