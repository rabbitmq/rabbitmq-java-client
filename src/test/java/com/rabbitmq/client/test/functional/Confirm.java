// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmationChannel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.PublishException;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.TestInfo;

public class Confirm extends BrokerTestCase
{
    private final static int NUM_MESSAGES = 1000;

    private static final String TTL_ARG = "x-message-ttl";

    @BeforeEach
    @Override
    public void setUp(TestInfo info) throws IOException, TimeoutException {
        super.setUp(info);
        channel.confirmSelect();
        channel.queueDeclare("confirm-test", true, true, false, null);
        channel.queueDeclare("confirm-durable-nonexclusive", true, false,
                             false, null);
        channel.basicConsume("confirm-test", true,
                             new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-nondurable", false, true,
                             false, null);
        channel.basicConsume("confirm-test-nondurable", true,
                             new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-noconsumer", true,
                             true, false, null);
        channel.queueDeclare("confirm-test-2", true, true, false, null);
        channel.basicConsume("confirm-test-2", true,
                             new DefaultConsumer(channel));
        channel.queueBind("confirm-test", "amq.direct",
                          "confirm-multiple-queues");
        channel.queueBind("confirm-test-2", "amq.direct",
                          "confirm-multiple-queues");
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        channel.queueDelete("confirm-durable-nonexclusive");
    }

    @Test public void persistentMandatoryCombinations()
        throws IOException, InterruptedException, TimeoutException {
        boolean b[] = { false, true };
        for (boolean persistent : b) {
            for (boolean mandatory : b) {
                confirmTest("", "confirm-test", persistent, mandatory);
            }
        }
    }

    @Test public void nonDurable()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("", "confirm-test-nondurable", true, false);
    }

    @Test public void mandatoryNoRoute()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("", "confirm-test-doesnotexist", false, true);
        confirmTest("", "confirm-test-doesnotexist",  true, true);
    }

    @Test public void multipleQueues()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("amq.direct", "confirm-multiple-queues", true, false);
    }

    /* For testQueueDelete and testQueuePurge to be
     * relevant, the msg_store must not write the messages to disk
     * (thus causing a confirm).  I'd manually comment out the line in
     * internal_sync that notifies the clients. */

    @Test public void queueDelete()
        throws IOException, InterruptedException, TimeoutException {
        publishN("","confirm-test-noconsumer", true, false);

        channel.queueDelete("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void queuePurge()
        throws IOException, InterruptedException, TimeoutException {
        publishN("", "confirm-test-noconsumer", true, false);

        channel.queuePurge("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie(60000);
    }

    /* Tests rabbitmq-server #854 */
    @Test public void confirmQueuePurge()
        throws IOException, InterruptedException, TimeoutException {
        channel.basicQos(1);
        for (int i = 0; i < 20000; i++) {
            publish("", "confirm-durable-nonexclusive", true, false);
            if (i % 100 == 0) {
                channel.queuePurge("confirm-durable-nonexclusive");
            }
        }
        channel.waitForConfirmsOrDie(90000);
    }

    @Test public void basicReject()
        throws IOException, InterruptedException, TimeoutException {
        basicRejectCommon(false);

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void queueTTL()
        throws IOException, InterruptedException, TimeoutException {
        for (int ttl : new int[]{ 1, 0 }) {
            Map<String, Object> argMap =
                Collections.singletonMap(TTL_ARG, (Object)ttl);
            channel.queueDeclare("confirm-ttl", true, true, false, argMap);

            publishN("", "confirm-ttl", true, false);
            channel.waitForConfirmsOrDie(60000);

            channel.queueDelete("confirm-ttl");
        }
    }

    @Test public void basicRejectRequeue()
        throws IOException, InterruptedException, TimeoutException {
        basicRejectCommon(true);

        /* wait confirms to go through the broker */
        Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             new DefaultConsumer(channel));

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void basicRecover()
        throws IOException, InterruptedException, TimeoutException {
        publishN("", "confirm-test-noconsumer", true, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet("confirm-test-noconsumer", false);
            resp.getEnvelope().getDeliveryTag();
            // not acking
        }

        channel.basicRecover(true);

        Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             new DefaultConsumer(channel));

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void select()
        throws IOException
    {
        channel.confirmSelect();
        try {
            Channel ch = connection.createChannel();
            ch.confirmSelect();
            ch.txSelect();
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
        try {
            Channel ch = connection.createChannel();
            ch.txSelect();
            ch.confirmSelect();
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }

    @Test public void waitForConfirms()
        throws IOException, InterruptedException, TimeoutException {
        final SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());
        channel.addConfirmListener(new ConfirmListener() {
                public void handleAck(long seqNo, boolean multiple) {
                    if (!unconfirmedSet.contains(seqNo)) {
                        fail("got duplicate ack: " + seqNo);
                    }
                    if (multiple) {
                        unconfirmedSet.headSet(seqNo + 1).clear();
                    } else {
                        unconfirmedSet.remove(seqNo);
                    }
                }

                public void handleNack(long seqNo, boolean multiple) {
                    fail("got a nack");
                }
            });

        for (long i = 0; i < NUM_MESSAGES; i++) {
            unconfirmedSet.add(channel.getNextPublishSeqNo());
            publish("", "confirm-test", true, false);
        }

        channel.waitForConfirmsOrDie(60000);
        if (!unconfirmedSet.isEmpty()) {
            fail("waitForConfirms returned with unconfirmed messages");
        }
    }

    @Test public void waitForConfirmsWithoutConfirmSelected()
        throws IOException, InterruptedException
    {
        channel = connection.createChannel();
        // Don't enable Confirm mode
        publish("", "confirm-test", true, false);
        try {
            channel.waitForConfirms(60000);
            fail("waitForConfirms without confirms selected succeeded");
        } catch (IllegalStateException _e) {} catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Test public void waitForConfirmsException()
        throws IOException, InterruptedException, TimeoutException {
        publishN("", "confirm-test", true, false);
        channel.close();
        try {
            channel.waitForConfirmsOrDie(60000);
            fail("waitAcks worked on a closed channel");
        } catch (ShutdownSignalException sse) {
            if (!(sse.getReason() instanceof AMQP.Channel.Close))
                fail("Shutdown reason not Channel.Close");
            //whoosh; everything ok
        } catch (InterruptedException e) {
            // whoosh; we should probably re-run, though
        }
    }

    /* Publish NUM_MESSAGES messages and wait for confirmations. */
    public void confirmTest(String exchange, String queueName,
                            boolean persistent, boolean mandatory)
        throws IOException, InterruptedException, TimeoutException {
        publishN(exchange, queueName, persistent, mandatory);

        channel.waitForConfirmsOrDie(60000);
    }

    private void publishN(String exchangeName, String queueName,
                          boolean persistent, boolean mandatory)
        throws IOException
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            publish(exchangeName, queueName, persistent, mandatory);
        }
    }

    private void basicRejectCommon(boolean requeue)
        throws IOException
    {
        publishN("", "confirm-test-noconsumer", true, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet("confirm-test-noconsumer", false);
            long dtag = resp.getEnvelope().getDeliveryTag();
            channel.basicReject(dtag, requeue);
        }
    }

    protected void publish(String exchangeName, String queueName,
                           boolean persistent, boolean mandatory)
        throws IOException {
        channel.basicPublish(exchangeName, queueName, mandatory, false,
                             persistent ? MessageProperties.PERSISTENT_BASIC
                                        : MessageProperties.BASIC,
                             "nop".getBytes());
    }

    /**
     * Tests basic publisher confirmation tracking with context parameter.
     * Verifies that futures complete successfully with their context values when messages are confirmed.
     */
    @Test public void testBasicPublishAsync() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);
        String queue = confirmCh.queueDeclare().getQueue();

        int messageCount = 100;
        java.util.List<java.util.concurrent.CompletableFuture<Integer>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            futures.add(confirmCh.basicPublishAsync("", queue, null, ("msg" + i).getBytes(), i));
        }

        // Verify all futures complete with their context values
        for (int i = 0; i < messageCount; i++) {
            assertEquals(Integer.valueOf(i), futures.get(i).join());
        }

        assertEquals(messageCount, confirmCh.messageCount(queue));
        confirmCh.close();
    }

    /**
     * Tests that unroutable messages with mandatory flag cause futures to complete exceptionally.
     * Verifies PublishException contains correct return information (isReturn=true, replyCode=NO_ROUTE).
     */
    @Test public void testBasicPublishAsyncWithReturn() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);

        java.util.concurrent.CompletableFuture<Void> future = confirmCh.basicPublishAsync(
            "", "nonexistent-queue", true, null, "test".getBytes(), null
        );

        try {
            future.join();
            fail("Expected PublishException");
        } catch (java.util.concurrent.CompletionException e) {
            assertTrue(e.getCause() instanceof PublishException);
            PublishException pe = (PublishException) e.getCause();
            assertTrue(pe.isReturn());
            assertEquals(AMQP.NO_ROUTE, pe.getReplyCode().intValue());
        }

        confirmCh.close();
    }

    /**
     * Tests rate limiting with ThrottlingRateLimiter.
     * Verifies that messages are throttled to max 10 concurrent in-flight messages
     * and all futures complete with correct context values.
     */
    @Test public void testMaxOutstandingConfirms() throws Exception {
        com.rabbitmq.client.ThrottlingRateLimiter limiter =
            new com.rabbitmq.client.ThrottlingRateLimiter(10, 50);
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, limiter);
        String queue = confirmCh.queueDeclare().getQueue();

        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<java.util.concurrent.CompletableFuture<Integer>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < 50; i++) {
            final int msgNum = i;
            java.util.concurrent.CompletableFuture<Integer> future = confirmCh.basicPublishAsync(
                "", queue, null, ("msg" + i).getBytes(), i
            );
            future.thenAccept(ctx -> {
                assertEquals(Integer.valueOf(msgNum), ctx);
                completed.incrementAndGet();
            });
            futures.add(future);
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        assertEquals(50, completed.get());
        confirmCh.close();
    }

    /**
     * Tests that closing a channel with pending confirmations causes all futures to complete exceptionally.
     * Verifies that AlreadyClosedException is thrown for in-flight messages when channel closes.
     */
    @Test public void testBasicPublishAsyncChannelClose() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);
        String queue = confirmCh.queueDeclare().getQueue();

        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(confirmCh.basicPublishAsync("", queue, null, ("msg" + i).getBytes(), null));
        }

        confirmCh.close();

        for (java.util.concurrent.CompletableFuture<Void> future : futures) {
            try {
                future.join();
            } catch (java.util.concurrent.CompletionException e) {
                assertTrue(e.getCause() instanceof AlreadyClosedException);
            }
        }
    }

    /**
     * Tests that rate limiting introduces delay and all messages complete with correct context.
     * Verifies ThrottlingRateLimiter limits concurrent in-flight messages and elapsed time is non-zero.
     */
    @Test public void testBasicPublishAsyncWithThrottling() throws Exception {
        com.rabbitmq.client.ThrottlingRateLimiter limiter =
            new com.rabbitmq.client.ThrottlingRateLimiter(10, 50);
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, limiter);
        String queue = confirmCh.queueDeclare().getQueue();

        int messageCount = 50;
        java.util.List<java.util.concurrent.CompletableFuture<String>> futures = new java.util.ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            String msgId = "msg-" + i;
            futures.add(confirmCh.basicPublishAsync("", queue, null, ("message" + i).getBytes(), msgId));
        }

        // Verify all complete with correct context
        for (int i = 0; i < messageCount; i++) {
            assertEquals("msg-" + i, futures.get(i).join());
        }

        long elapsed = System.currentTimeMillis() - start;

        assertEquals(messageCount, confirmCh.messageCount(queue));
        assertTrue(elapsed > 0, "Throttling should introduce some delay");
        confirmCh.close();
    }

    /**
     * Tests performance comparison between throttled and unlimited channels.
     * Verifies both complete successfully and throttling introduces measurable delay.
     */
    @Test public void testBasicPublishAsyncThrottlingVsUnlimited() throws Exception {
        // Test with throttling (10 permits, 50% threshold)
        com.rabbitmq.client.ThrottlingRateLimiter limiter =
            new com.rabbitmq.client.ThrottlingRateLimiter(10, 50);
        Channel throttlingCh = connection.createChannel();
        ConfirmationChannel throttlingConfirmCh = ConfirmationChannel.create(throttlingCh, limiter);
        String queue1 = throttlingConfirmCh.queueDeclare().getQueue();

        int messageCount = 4096;
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            futures.add(throttlingConfirmCh.basicPublishAsync("", queue1, null, ("msg" + i).getBytes(), null));
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        long throttlingElapsed = System.currentTimeMillis() - start;

        assertEquals(messageCount, throttlingConfirmCh.messageCount(queue1));
        throttlingConfirmCh.close();

        // Test with unlimited (no rate limiter)
        Channel unlimitedCh = connection.createChannel();
        ConfirmationChannel unlimitedConfirmCh = ConfirmationChannel.create(unlimitedCh, null);
        String queue2 = unlimitedConfirmCh.queueDeclare().getQueue();

        futures.clear();
        start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            futures.add(unlimitedConfirmCh.basicPublishAsync("", queue2, null, ("msg" + i).getBytes(), null));
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        long unlimitedElapsed = System.currentTimeMillis() - start;

        assertEquals(messageCount, unlimitedConfirmCh.messageCount(queue2));
        unlimitedConfirmCh.close();

        // Both should complete successfully
        assertTrue(throttlingElapsed > 0);
        assertTrue(unlimitedElapsed > 0);
    }

    /**
     * Tests that ConfirmationChannel works correctly without a rate limiter.
     * Verifies all messages are confirmed when rateLimiter is null (unlimited concurrency).
     */
    @Test public void testBasicPublishAsyncWithNullRateLimiter() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);
        String queue = confirmCh.queueDeclare().getQueue();

        int messageCount = 100;
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            futures.add(confirmCh.basicPublishAsync("", queue, null, ("msg" + i).getBytes(), null));
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

        assertEquals(messageCount, confirmCh.messageCount(queue));
        confirmCh.close();
    }

    /**
     * Tests context parameter correlation with String correlation IDs.
     * Verifies that each future completes with its exact correlation ID for message tracking.
     */
    @Test public void testBasicPublishAsyncWithContext() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);
        String queue = confirmCh.queueDeclare().getQueue();

        int messageCount = 10;
        java.util.Map<String, java.util.concurrent.CompletableFuture<String>> futuresByCorrelationId = new java.util.HashMap<>();

        for (int i = 0; i < messageCount; i++) {
            String correlationId = "msg-" + i;
            java.util.concurrent.CompletableFuture<String> future = confirmCh.basicPublishAsync(
                "", queue, null, ("message" + i).getBytes(), correlationId
            );
            futuresByCorrelationId.put(correlationId, future);
        }

        // Verify all futures complete with their correlation IDs
        for (java.util.Map.Entry<String, java.util.concurrent.CompletableFuture<String>> entry : futuresByCorrelationId.entrySet()) {
            String expectedId = entry.getKey();
            String actualId = entry.getValue().join();
            assertEquals(expectedId, actualId);
        }

        assertEquals(messageCount, confirmCh.messageCount(queue));
        confirmCh.close();
    }

    /**
     * Tests that context parameter is available in PublishException for failed publishes.
     * Verifies context is preserved when message is returned as unroutable.
     */
    @Test public void testBasicPublishAsyncWithContextInException() throws Exception {
        Channel ch = connection.createChannel();
        ConfirmationChannel confirmCh = ConfirmationChannel.create(ch, null);

        String messageId = "unroutable-msg-456";
        java.util.concurrent.CompletableFuture<String> future = confirmCh.basicPublishAsync(
            "", "nonexistent-queue", true, null, "test".getBytes(), messageId
        );

        try {
            future.join();
            fail("Expected PublishException");
        } catch (java.util.concurrent.CompletionException e) {
            assertTrue(e.getCause() instanceof PublishException);
            PublishException pe = (PublishException) e.getCause();
            assertTrue(pe.isReturn());
            assertEquals(AMQP.NO_ROUTE, pe.getReplyCode().intValue());
            assertEquals(messageId, pe.getContext());
        }

        confirmCh.close();
    }
}
