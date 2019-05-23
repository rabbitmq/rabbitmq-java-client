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

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class DeadLetterExchange extends BrokerTestCase {
    public static final String DLX = "dead.letter.exchange";
    private static final String DLX_ARG = "x-dead-letter-exchange";
    private static final String DLX_RK_ARG = "x-dead-letter-routing-key";
    public static final String TEST_QUEUE_NAME = "test.queue.dead.letter";
    public static final String DLQ = "queue.dlq";
    private static final String DLQ2 = "queue.dlq2";
    public static final int MSG_COUNT = 10;
    private static final int TTL = 2000;

    @Override
    protected void createResources() throws IOException {
        channel.exchangeDelete(DLX);
        channel.queueDelete(DLQ);
        channel.exchangeDeclare(DLX, "direct");
        channel.queueDeclare(DLQ, false, true, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.exchangeDelete(DLX);
        channel.queueDelete(TEST_QUEUE_NAME);
    }

    @Test public void declareQueueWithExistingDeadLetterExchange()
        throws IOException
    {
        declareQueue(DLX);
    }

    @Test public void declareQueueWithNonExistingDeadLetterExchange()
        throws IOException
    {
        declareQueue("some.random.exchange.name");
    }

    @Test public void declareQueueWithEquivalentDeadLetterExchange()
        throws IOException
    {
        declareQueue(DLX);
        declareQueue(DLX);
    }

    @Test public void declareQueueWithEquivalentDeadLetterRoutingKey()
        throws IOException
    {
        declareQueue(TEST_QUEUE_NAME, DLX, "routing_key", null);
        declareQueue(TEST_QUEUE_NAME, DLX, "routing_key", null);
    }

    @Test public void declareQueueWithInvalidDeadLetterExchangeArg() {
        try {
            declareQueue(133);
            fail("x-dead-letter-exchange must be a valid exchange name");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void redeclareQueueWithInvalidDeadLetterExchangeArg()
        throws IOException
    {
        declareQueue("inequivalent_dlx_name", "dlx_foo", null, null);
        try {
            declareQueue("inequivalent_dlx_name", "dlx_bar", null, null);
            fail("x-dead-letter-exchange must be a valid exchange name " +
                    "and must not change in subsequent declarations");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void declareQueueWithInvalidDeadLetterRoutingKeyArg() {
        try {
            declareQueue("foo", "amq.direct", 144, null);
            fail("x-dead-letter-routing-key must be a string");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void redeclareQueueWithInvalidDeadLetterRoutingKeyArg()
        throws IOException
    {
        declareQueue("inequivalent_dlx_rk", "amq.direct", "dlx_rk", null);
        try {
            declareQueue("inequivalent_dlx_rk", "amq.direct", "dlx_rk2", null);
            fail("x-dead-letter-routing-key must be a string and must not " +
                    "change in subsequent declarations");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void declareQueueWithRoutingKeyButNoDeadLetterExchange() {
        try {
            Map<String, Object> args = new HashMap<>();
            args.put(DLX_RK_ARG, "foo");

            channel.queueDeclare(randomQueueName(), false, true, false, args);
            fail("dlx must be defined if dl-rk is set");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void redeclareQueueWithRoutingKeyButNoDeadLetterExchange() {
        try {
            String queueName = randomQueueName();
            Map<String, Object> args = new HashMap<>();
            channel.queueDeclare(queueName, false, true, false, args);

            args.put(DLX_RK_ARG, "foo");

            channel.queueDeclare(queueName, false, true, false, args);
            fail("x-dead-letter-exchange must be specified if " +
                    "x-dead-letter-routing-key is set");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void deadLetterQueueTTLExpiredMessages() throws Exception {
        ttlTest(TTL);
    }

    @Test public void deadLetterQueueZeroTTLExpiredMessages() throws Exception {
        ttlTest(0);
    }

    @Test public void deadLetterQueueTTLPromptExpiry() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", TTL);
        declareQueue(TEST_QUEUE_NAME, DLX, null, args);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        //measure round-trip latency
        AccumulatingMessageConsumer c = new AccumulatingMessageConsumer(channel);
        String cTag = channel.basicConsume(TEST_QUEUE_NAME, true, c);
        long start = System.currentTimeMillis();
        publish(null, "test");
        byte[] body = c.nextDelivery(TTL);
        long stop = System.currentTimeMillis();
        assertNotNull(body);
        channel.basicCancel(cTag);
        long latency = stop-start;

        channel.basicConsume(DLQ, true, c);

        // publish messages at regular intervals until currentTime +
        // 3/4th of TTL
        int count = 0;
        start = System.currentTimeMillis();
        stop = start + TTL * 3 / 4;
        long now = start;
        while (now < stop) {
            publish(null, Long.toString(now));
            count++;
            Thread.sleep(TTL / 100);
            now = System.currentTimeMillis();
        }

        checkPromptArrival(c, count, latency);

        start = System.currentTimeMillis();
        // publish message - which kicks off the queue's ttl timer -
        // and immediately fetch it in noack mode
        publishAt(start);
        basicGet(TEST_QUEUE_NAME);
        // publish a 2nd message and immediately fetch it in ack mode
        publishAt(start + TTL / 2);
        GetResponse r = channel.basicGet(TEST_QUEUE_NAME, false);
        // publish a 3rd message
        publishAt(start + TTL * 3 / 4);
        // reject 2nd message after the initial timer has fired but
        // before the message is due to expire
        waitUntil(start + TTL * 5 / 4);
        channel.basicReject(r.getEnvelope().getDeliveryTag(), true);

        checkPromptArrival(c, 2, latency);
    }

    @Test public void deadLetterDeletedDLX() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        channel.exchangeDelete(DLX);
        publishN(MSG_COUNT);
        sleep(100);

        consumeN(DLQ, 0, WithResponse.NULL);

        channel.exchangeDeclare(DLX, "direct");
        channel.queueBind(DLQ, DLX, "test");

        publishN(MSG_COUNT);
        sleep(100);

        consumeN(DLQ, MSG_COUNT, WithResponse.NULL);
    }

    @SuppressWarnings("unchecked")
    @Test public void deadLetterPerMessageTTLRemoved() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        final BasicProperties props = MessageProperties.BASIC.builder().expiration("100").build();
        publish(props, "test message");

        // The message's expiration property should have been removed, thus
        // after 100ms of hitting the queue, the message should get routed to
        // the DLQ *AND* should remain there, not getting removed after a subsequent
        // wait time > 100ms
        sleep(500);
        consumeN(DLQ, 1, getResponse -> {
            assertNull(getResponse.getProps().getExpiration());
            Map<String, Object> headers = getResponse.getProps().getHeaders();
            assertNotNull(headers);
            ArrayList<Object> death = (ArrayList<Object>) headers.get("x-death");
            assertNotNull(death);
            assertDeathReason(death, 0, TEST_QUEUE_NAME, "expired");
            final Map<String, Object> deathHeader = (Map<String, Object>) death.get(0);
            assertEquals("100", deathHeader.get("original-expiration").toString());
        });
    }

    @Test public void deadLetterOnReject() throws Exception {
        rejectionTest(false);
    }

    @Test public void deadLetterOnNack() throws Exception {
        rejectionTest(true);
    }

    @Test public void deadLetterNoDeadLetterQueue() throws IOException {
        channel.queueDelete(DLQ);

        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");

        publishN(MSG_COUNT);
    }

    @Test public void deadLetterMultipleDeadLetterQueues()
        throws IOException
    {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);

        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test");

        publishN(MSG_COUNT);
    }

    @SuppressWarnings("unchecked")
    @Test public void deadLetterTwice() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);

        channel.queueDelete(DLQ);
        declareQueue(DLQ, DLX, null, null, 1);

        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test");

        publishN(MSG_COUNT);

        sleep(100);

        // There should now be two copies of each message on DLQ2: one
        // with one set of death headers, and another with two sets.
        consumeN(DLQ2, MSG_COUNT*2, getResponse -> {
            Map<String, Object> headers = getResponse.getProps().getHeaders();
            assertNotNull(headers);
            ArrayList<Object> death = (ArrayList<Object>) headers.get("x-death");
            assertNotNull(death);
            if (death.size() == 1) {
                assertDeathReason(death, 0, TEST_QUEUE_NAME, "expired");
            } else if (death.size() == 2) {
                assertDeathReason(death, 0, DLQ, "expired");
                assertDeathReason(death, 1, TEST_QUEUE_NAME, "expired");
            } else {
                fail("message was dead-lettered more times than expected");
            }
        });
    }

    @Test public void deadLetterSelf() throws Exception {
        declareQueue(TEST_QUEUE_NAME, "amq.direct", "test", null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");

        publishN(MSG_COUNT);
        // This test hangs if the queue doesn't process ALL the
        // messages before being deleted, so make sure the next
        // sleep is long enough.
        sleep(200);

        // The messages will NOT be dead-lettered to self.
        consumeN(TEST_QUEUE_NAME, 0, WithResponse.NULL);
    }

    @Test public void deadLetterCycle() throws Exception {
        // testDeadLetterTwice and testDeadLetterSelf both test that we drop
        // messages in pure-expiry cycles. So we just need to test that
        // non-pure-expiry cycles do not drop messages.

        declareQueue("queue1", "", "queue2", null, 1);
        declareQueue("queue2", "", "queue1", null, 0);

        channel.basicPublish("", "queue1", MessageProperties.BASIC, "".getBytes());
        final CountDownLatch latch = new CountDownLatch(10);
        channel.basicConsume("queue2", false,
            new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    channel.basicReject(envelope.getDeliveryTag(), false);
                    latch.countDown();
                }
            });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @SuppressWarnings("unchecked")
    @Test public void deadLetterNewRK() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, "test-other", null, 1);

        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test-other");

        Map<String, Object> headers = new HashMap<>();
        headers.put("CC", Collections.singletonList("foo"));
        headers.put("BCC", Collections.singletonList("bar"));

        publishN(MSG_COUNT, (new AMQP.BasicProperties.Builder())
                               .headers(headers)
                               .build());

        sleep(100);

        consumeN(DLQ, 0, WithResponse.NULL);
        consumeN(DLQ2, MSG_COUNT, getResponse -> {
            Map<String, Object> headers1 = getResponse.getProps().getHeaders();
            assertNotNull(headers1);
            assertNull(headers1.get("CC"));
            assertNull(headers1.get("BCC"));

            ArrayList<Object> death = (ArrayList<Object>) headers1.get("x-death");
            assertNotNull(death);
            assertEquals(1, death.size());
            assertDeathReason(death, 0, TEST_QUEUE_NAME,
                              "expired", "amq.direct",
                              Arrays.asList("test", "foo"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test public void republish() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 100);
        declareQueue(TEST_QUEUE_NAME, DLX, null, args);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        publishN(1);

        sleep(200);

        GetResponse getResponse = channel.basicGet(DLQ, true);
        assertNotNull("Message not dead-lettered",
            getResponse);
        assertEquals("test message", new String(getResponse.getBody()));
        BasicProperties props = getResponse.getProps();
        Map<String, Object> headers = props.getHeaders();
        assertNotNull(headers);
        ArrayList<Object> death = (ArrayList<Object>) headers.get("x-death");
        assertNotNull(death);
        assertEquals(1, death.size());
        assertDeathReason(death, 0, TEST_QUEUE_NAME, "expired", "amq.direct",
                Collections.singletonList("test"));

        // Make queue zero length
        args = new HashMap<>();
        args.put("x-max-length", 0);
        channel.queueDelete(TEST_QUEUE_NAME);
        declareQueue(TEST_QUEUE_NAME, DLX, null, args);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");

        sleep(100);
        //Queueing second time with same props
        channel.basicPublish("amq.direct", "test",
            new AMQP.BasicProperties.Builder()
               .headers(headers)
               .build(), "test message".getBytes());

        sleep(100);

        getResponse = channel.basicGet(DLQ, true);
        assertNotNull("Message not dead-lettered", getResponse);
        assertEquals("test message", new String(getResponse.getBody()));
        headers = getResponse.getProps().getHeaders();
        assertNotNull(headers);
        death = (ArrayList<Object>) headers.get("x-death");
        assertNotNull(death);
        assertEquals(2, death.size());
        assertDeathReason(death, 0, TEST_QUEUE_NAME, "maxlen", "amq.direct",
                Collections.singletonList("test"));
        assertDeathReason(death, 1, TEST_QUEUE_NAME, "expired", "amq.direct",
                Collections.singletonList("test"));

        //Set invalid headers
        headers.put("x-death", "[I, am, not, array]");
        channel.basicPublish("amq.direct", "test",
            new AMQP.BasicProperties.Builder()
               .headers(headers)
               .build(), "test message".getBytes());
        sleep(100);

        getResponse = channel.basicGet(DLQ, true);
        assertNotNull("Message not dead-lettered", getResponse);
        assertEquals("test message", new String(getResponse.getBody()));
        headers = getResponse.getProps().getHeaders();
        assertNotNull(headers);
        death = (ArrayList<Object>) headers.get("x-death");
        assertNotNull(death);
        assertEquals(1, death.size());
        assertDeathReason(death, 0, TEST_QUEUE_NAME, "maxlen", "amq.direct",
                Collections.singletonList("test"));

    }

    private void rejectionTest(final boolean useNack) throws Exception {
        deadLetterTest((Callable<Void>) () -> {
            for (int x = 0; x < MSG_COUNT; x++) {
                GetResponse getResponse =
                    channel.basicGet(TEST_QUEUE_NAME, false);
                long tag = getResponse.getEnvelope().getDeliveryTag();
                if (useNack) {
                    channel.basicNack(tag, false, false);
                } else {
                    channel.basicReject(tag, false);
                }
            }
            return null;
        }, null, "rejected");
    }

    private void deadLetterTest(final Runnable deathTrigger,
                                Map<String, Object> queueDeclareArgs,
                                String reason)
        throws Exception
    {
        deadLetterTest(() -> {
            deathTrigger.run();
            return null;
        }, queueDeclareArgs, reason);
    }

    private void deadLetterTest(Callable<?> deathTrigger,
                                Map<String, Object> queueDeclareArgs,
                                final String reason)
        throws Exception
    {
        declareQueue(TEST_QUEUE_NAME, DLX, null, queueDeclareArgs);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        publishN(MSG_COUNT);

        deathTrigger.call();

        consume(channel, reason);
    }

    @SuppressWarnings("unchecked")
    public static void consume(final Channel channel, final String reason) throws IOException {
        consumeN(channel, DLQ, MSG_COUNT, getResponse -> {
            Map<String, Object> headers = getResponse.getProps().getHeaders();
            assertNotNull(headers);
            ArrayList<Object> death = (ArrayList<Object>) headers.get("x-death");
            assertNotNull(death);
            // the following assertions shouldn't be checked on version lower than 3.7
            // as the headers are new in 3.7
            // see https://github.com/rabbitmq/rabbitmq-server/issues/1332
            if(TestUtils.isVersion37orLater(channel.getConnection())) {
                assertNotNull(headers.get("x-first-death-queue"));
                assertNotNull(headers.get("x-first-death-reason"));
                assertNotNull(headers.get("x-first-death-exchange"));
            }
            assertEquals(1, death.size());
            assertDeathReason(death, 0, TEST_QUEUE_NAME, reason,
                    "amq.direct",
                    Collections.singletonList("test"));
        });
    }

    private void ttlTest(final long ttl) throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", ttl);
        deadLetterTest(() -> sleep(ttl + 1500), args, "expired");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // whoosh
        }
    }

    /* check that each message arrives within epsilon of the
       publication time + TTL + latency */
    private void checkPromptArrival(AccumulatingMessageConsumer c,
                                    int count, long latency) throws Exception {
        long epsilon = TTL / 5;
        for (int i = 0; i < count; i++) {
            byte[] body = c.nextDelivery(TTL + TTL + latency + epsilon);
            assertNotNull("message #" + i + " did not expire", body);
            long now = System.currentTimeMillis();
            long publishTime = Long.valueOf(new String(body));
            long targetTime = publishTime + TTL + latency;
            assertTrue("expiry outside bounds (+/- " + epsilon + "): " +
                       (now - targetTime),
                       (now >= targetTime - epsilon) &&
                       (now <= targetTime + epsilon));
        }
    }

    private void declareQueue(Object deadLetterExchange) throws IOException {
        declareQueue(TEST_QUEUE_NAME, deadLetterExchange, null, null);
    }

    private void declareQueue(String queue, Object deadLetterExchange,
                              Object deadLetterRoutingKey,
                              Map<String, Object> args) throws IOException {
        declareQueue(queue, deadLetterExchange, deadLetterRoutingKey, args, 0);
    }

    private void declareQueue(String queue, Object deadLetterExchange,
                              Object deadLetterRoutingKey,
                              Map<String, Object> args, int ttl)
        throws IOException
    {
        if (args == null) {
            args = new HashMap<>();
        }

        if (ttl > 0){
            args.put("x-message-ttl", ttl);
        }

        args.put(DLX_ARG, deadLetterExchange);
        if (deadLetterRoutingKey != null) {
            args.put(DLX_RK_ARG, deadLetterRoutingKey);
        }
        channel.queueDeclare(queue, false, false, false, args);
    }

    private void publishN(int n) throws IOException {
        publishN(n, null);
    }

    private void publishN(int n, AMQP.BasicProperties props)
        throws IOException
    {
        for(int x = 0; x < n; x++) { publish(props, "test message"); }
    }

    private void publish(AMQP.BasicProperties props, String body)
        throws IOException
    {
        channel.basicPublish("amq.direct", "test", props, body.getBytes());
    }

    private void publishAt(long when) throws Exception {
        waitUntil(when);
        publish(null, Long.toString(System.currentTimeMillis()));
    }

    private void waitUntil(long when) throws Exception {
        long delay = when - System.currentTimeMillis();
        Thread.sleep(delay > 0 ? delay : 0);
    }

    private void consumeN(String queue, int n, WithResponse withResponse)
        throws IOException
    {
        consumeN(channel, queue, n, withResponse);
    }

    private static void consumeN(Channel channel, String queue, int n, WithResponse withResponse)
        throws IOException
    {
        for(int x = 0; x < n; x++) {
            GetResponse getResponse =
                channel.basicGet(queue, true);
            assertNotNull("Messages not dead-lettered (" + (n-x) + " left)",
                          getResponse);
            assertEquals("test message", new String(getResponse.getBody()));
            withResponse.process(getResponse);
        }
        GetResponse getResponse = channel.basicGet(queue, true);
        assertNull("expected empty queue", getResponse);
    }

    @SuppressWarnings("unchecked")
    private static void assertDeathReason(List<Object> death, int num,
                                   String queue, String reason,
                                   String exchange, List<String> routingKeys)
    {
        Map<String, Object> deathHeader =
            (Map<String, Object>)death.get(num);
        assertEquals(exchange, deathHeader.get("exchange").toString());

        List<String> deathRKs = new ArrayList<>();
        for (Object rk : (ArrayList<?>)deathHeader.get("routing-keys")) {
            deathRKs.add(rk.toString());
        }
        Collections.sort(deathRKs);
        Collections.sort(routingKeys);
        assertEquals(routingKeys, deathRKs);

        assertDeathReason(death, num, queue, reason);
    }

    @SuppressWarnings("unchecked")
    private static void assertDeathReason(List<Object> death, int num,
                                   String queue, String reason) {
        Map<String, Object> deathHeader =
            (Map<String, Object>)death.get(num);
        assertEquals(queue, deathHeader.get("queue").toString());
        assertEquals(reason, deathHeader.get("reason").toString());
    }

    private interface WithResponse {
        WithResponse NULL = getResponse -> {
        };

        void process(GetResponse response);
    }

    private static String randomQueueName() {
        return DeadLetterExchange.class.getSimpleName() + "-" + UUID.randomUUID().toString();
    }

    class AccumulatingMessageConsumer extends DefaultConsumer {

        BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();

        AccumulatingMessageConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            messages.add(body);
        }

        byte[] nextDelivery() {
            return messages.poll();
        }

        byte[] nextDelivery(long timeoutInMs) throws InterruptedException {
            return messages.poll(timeoutInMs, TimeUnit.MILLISECONDS);
        }

    }
}
