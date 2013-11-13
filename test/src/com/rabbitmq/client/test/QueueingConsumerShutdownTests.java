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

package com.rabbitmq.client.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingCell;

public class QueueingConsumerShutdownTests extends BrokerTestCase {
    static final String QUEUE = "some-queue";
    static final int THREADS = 5;
    private static final int CONSUMER_COUNT = 10;

    public void testNThreadShutdown() throws Exception {
        final QueueingConsumer c = new QueueingConsumer(channel);
        channel.queueDeclare(QUEUE, false, true, true, null);
        channel.basicConsume(QUEUE, c);
        final AtomicInteger count = new AtomicInteger(THREADS);
        final CountDownLatch latch = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            c.nextDelivery();
                        }
                    } catch (ShutdownSignalException sig) {
                        count.decrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                }
            }.start();
        }

        connection.close();

        // Far longer than this could reasonably take
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(0, count.get());
    }

    public void testNConsumerShutdown() throws Exception {
        final Channel publisherChannel = connection.createChannel();
        final QueueingConsumer qc = new QueueingConsumer(channel);

        final List<String> queues = new ArrayList<String>(CONSUMER_COUNT);
        final CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            final String queueName = Integer.toString(i);
            final AMQP.Queue.DeclareOk declare =
                    channel.queueDeclare(queueName, false,
                                         true, false, null);
            final String queue = declare.getQueue();
            queues.add(queue);
            new Thread() {
                @Override
                public void run() {
                    try {
                        latch.await();
                        final AMQP.BasicProperties props = new AMQP.BasicProperties()
                                .builder()
                                .correlationId(queue)
                                .build();
                        publisherChannel.basicPublish("", queue, props, "".getBytes());
                    } catch (Exception e) {
                        fail(e.getMessage());
                    }
                }
            }.start();
        }

        for (final String queue : queues) {
            channel.basicConsume(queue, true, qc);
        }

        latch.countDown();

        final String queueToDeclareIncorrectly = queues.get(0);
        while (!queues.isEmpty()) {
            final QueueingConsumer.Delivery delivery = qc.nextDelivery();
            assertNotNull(delivery);

            final String q = delivery.getProperties().getCorrelationId();
            assertTrue("expected queues to contain " + q + ", but it didn't!",
                       queues.remove(q));
        }

        try {
            channel.queueDeclare(queueToDeclareIncorrectly, true, true, true, null);
        } catch (IOException e) {
            try { qc.nextDelivery(); }
            catch (ShutdownSignalException ex) { return; }
            fail("Expected ShutdownSignalException, but nothing was thrown.");
        }
        fail();
    }

    public void testNConsumerCancellation() throws Exception {
        final QueueingConsumer qc = new QueueingConsumer(channel);
        final Stack<String> queues = new Stack<String>();
        final BlockingCell<Boolean> result = new BlockingCell<Boolean>();

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            final String queue = Integer.toString(i);
            channel.queueDeclare(queue, false, true, false, null);
            channel.basicConsume(queue, qc);
            queues.push(queue);
        }

        new Thread() {
            @Override public void run() {
                try {
                    qc.nextDelivery();
                } catch (ConsumerCancelledException e) {
                    result.set(true);
                } catch (Exception e) {
                    result.set(false);
                }
            }
        }.start();

        while (queues.size() > 1) {
            channel.queueDelete(queues.pop());
        }

        TimeoutException timeoutException = null;
        try {
            result.get(500);
        } catch (TimeoutException tEx) {
            timeoutException = tEx;
        }

        assertNotNull("Consumer should not have thrown an exception yet!", timeoutException);

        channel.queueDelete(queues.pop());

        assertTrue("Expected ConsumerCancelException to be thrown", result.get());
    }

    public void testFailureModes() throws Exception {
        final QueueingConsumer qc = new QueueingConsumer(channel);
        createTestQueue("q1", qc);
        channel.queueDelete("q1");

        // the first basic.cancel arrived when we had only one consumer!
        checkForCancellationSignal(qc);

        createTestQueue("q2", qc);
        basicPublishVolatile("q2");
        basicPublishVolatile("q2");
        basicPublishVolatile("q2");
        channel.queueDelete("q2");

        // cancellation can happen at any time, but doesn't invalidate our object
        cancellationShouldThrowButNotTerminateAllConsumers(qc);

        // given an active consumer however...
        createTestQueue("q3", qc);

        // whereas shutdown signals do cause us to enter a permanent (shutdown) state
        shutdownStateShouldBePermanent(qc);
    }

    private void cancellationShouldThrowButNotTerminateAllConsumers(QueueingConsumer qc) throws InterruptedException {
        assertNotNull(qc.nextDelivery());
        assertNotNull(qc.nextDelivery());
        assertNotNull(qc.nextDelivery());

        checkForCancellationSignal(qc);
    }

    private void checkForCancellationSignal(QueueingConsumer qc) throws InterruptedException {
        try {
            qc.nextDelivery();
        } catch (ConsumerCancelledException cEx) {
            return;
        }
        fail("Expected Consumer Cancelled Exception, but wasn't thrown!");
    }

    private void shutdownStateShouldBePermanent(QueueingConsumer qc) throws IOException, InterruptedException {
        connection.close();
        checkForShutdownSignal(qc);
        checkForShutdownSignal(qc);
    }

    private static void checkForShutdownSignal(QueueingConsumer qc) throws InterruptedException {
        try {
            qc.nextDelivery();
        } catch (ShutdownSignalException sEx) {
            return;
        }
        fail("Expect Shutdown Signal Exception, but was not thrown!");
    }

    private void createTestQueue(final String queue, final Consumer qc) throws IOException {
        channel.queueDeclare(queue, false, true, true, null);
        channel.basicConsume(queue, qc);
    }

}
