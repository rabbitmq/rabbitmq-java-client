package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DeadLetterExchange extends BrokerTestCase {
    public static final String DLX = "dead.letter.exchange";
    public static final String DLX_ARG = "x-dead-letter-exchange";
    public static final String DLX_RK_ARG = "x-dead-letter-routing-key";
    public static final String TEST_QUEUE_NAME = "test.queue.dead.letter";
    public static final String DLQ = "queue.dlq";
    public static final String DLQ2 = "queue.dlq2";
    public static final int MSG_COUNT = 10;
    public static final int MSG_COUNT_MANY = 1000;
    public static final int TTL = 1000;

    @Override
    protected void createResources() throws IOException {
        channel.exchangeDeclare(DLX, "direct");
        channel.queueDeclare(DLQ, false, true, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.exchangeDelete(DLX);
    }

    public void testDeclareQueueWithExistingDeadLetterExchange()
        throws IOException
    {
        declareQueue(DLX);
    }

    public void testDeclareQueueWithNonExistingDeadLetterExchange()
        throws IOException
    {
        declareQueue("some.random.exchange.name");
    }

    public void testDeclareQueueWithInvalidDeadLetterExchangeArg()
        throws IOException
    {
        try {
            declareQueue(133);
            fail("x-dead-letter-exchange must be a valid exchange name");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testDeclareQueueWithInvalidDeadLetterRoutingKeyArg()
        throws IOException
    {
        try {
            declareQueue("foo", "amq.direct", 144, null);
            fail("x-dead-letter-routing-key must be a string");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testDeclareQueueWithRoutingKeyButNoDeadLetterExchange()
        throws IOException
    {
        try {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put(DLX_RK_ARG, "foo");

            channel.queueDeclare("bar", false, true, false, args);
            fail("dlx must be defined if dl-rk is set");
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }

    }

    public void testDeadLetterQueueTTLExpiredMessages() throws Exception {
        ttlTest(TTL);
    }

    public void testDeadLetterQueueZeroTTLExpiredMessages() throws Exception {
        ttlTest(0);
    }

    public void testDeadLetterQueueTTLPromptExpiry() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", TTL);
        declareQueue(TEST_QUEUE_NAME, DLX, null, args);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        //measure round-trip latency
        QueueingConsumer c = new QueueingConsumer(channel);
        String cTag = channel.basicConsume(TEST_QUEUE_NAME, true, c);
        long start = System.currentTimeMillis();
        publish(null, "test");
        Delivery d = c.nextDelivery(TTL);
        long stop = System.currentTimeMillis();
        assertNotNull(d);
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
        publishAt(start + TTL * 1 / 2);
        GetResponse r = channel.basicGet(TEST_QUEUE_NAME, false);
        // publish a 3rd message
        publishAt(start + TTL * 3 / 4);
        // reject 2nd message after the initial timer has fired but
        // before the message is due to expire
        waitUntil(start + TTL * 5 / 4);
        channel.basicReject(r.getEnvelope().getDeliveryTag(), true);

        checkPromptArrival(c, 2, latency);
    }

    public void testDeadLetterDeletedDLX() throws Exception {
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

    public void testDeadLetterExchangeDeleteTwice()
        throws IOException
    {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");

        publishN(MSG_COUNT_MANY);
        channel.queueDelete(TEST_QUEUE_NAME);
        try {
            channel.queueDelete(TEST_QUEUE_NAME);
            fail();
        } catch (IOException ex) {
            checkShutdownSignal(AMQP.NOT_FOUND, ex);
        }
    }

    public void testDeadLetterOnReject() throws Exception {
        rejectionTest(false);
    }

    public void testDeadLetterOnNack() throws Exception {
        rejectionTest(true);
    }

    public void testDeadLetterNoDeadLetterQueue() throws IOException {
        channel.queueDelete(DLQ);

        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");

        publishN(MSG_COUNT);
    }

    public void testDeadLetterMultipleDeadLetterQueues()
        throws IOException
    {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);

        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test");

        publishN(MSG_COUNT);
    }

    public void testDeadLetterTwice() throws Exception {
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
        consumeN(DLQ2, MSG_COUNT*2, new WithResponse() {
                @SuppressWarnings("unchecked")
                public void process(GetResponse getResponse) {
                    Map<String, Object> headers = getResponse.getProps().getHeaders();
                    assertNotNull(headers);
                    ArrayList<Object> death = (ArrayList<Object>)headers.get("x-death");
                    assertNotNull(death);
                    if (death.size() == 1) {
                        assertDeathReason(death, 0, TEST_QUEUE_NAME, "expired");
                    } else if (death.size() == 2) {
                        assertDeathReason(death, 0, DLQ, "expired");
                        assertDeathReason(death, 1, TEST_QUEUE_NAME, "expired");
                    } else {
                        fail("message was dead-lettered more times than expected");
                    }
                }
            });
    }

    public void testDeadLetterSelf() throws Exception {
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

    public void testDeadLetterNewRK() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, "test-other", null, 1);

        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test-other");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("CC", Arrays.asList(new String[]{"foo"}));
        headers.put("BCC", Arrays.asList(new String[]{"bar"}));

        publishN(MSG_COUNT, (new AMQP.BasicProperties.Builder())
                               .headers(headers)
                               .build());

        sleep(100);

        consumeN(DLQ, 0, WithResponse.NULL);
        consumeN(DLQ2, MSG_COUNT, new WithResponse() {
                @SuppressWarnings("unchecked")
                public void process(GetResponse getResponse) {
                    Map<String, Object> headers = getResponse.getProps().getHeaders();
                    assertNotNull(headers);
                    assertNull(headers.get("CC"));
                    assertNull(headers.get("BCC"));

                    ArrayList<Object> death = (ArrayList<Object>)headers.get("x-death");
                    assertNotNull(death);
                    assertEquals(1, death.size());
                    assertDeathReason(death, 0, TEST_QUEUE_NAME,
                                      "expired", "amq.direct",
                                      Arrays.asList(new String[]{"test", "foo"}));
                }
            });
    }

    public void rejectionTest(final boolean useNack) throws Exception {
        deadLetterTest(new Callable<Void>() {
                public Void call() throws Exception {
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
                }
            }, null, "rejected");
    }

    private void deadLetterTest(final Runnable deathTrigger,
                                Map<String, Object> queueDeclareArgs,
                                String reason)
        throws Exception
    {
        deadLetterTest(new Callable<Object>() {
                public Object call() throws Exception {
                    deathTrigger.run();
                    return null;
                }
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

    public static void consume(final Channel channel, final String reason) throws IOException {
        consumeN(channel, DLQ, MSG_COUNT, new WithResponse() {
            @SuppressWarnings("unchecked")
            public void process(GetResponse getResponse) {
                Map<String, Object> headers = getResponse.getProps().getHeaders();
                assertNotNull(headers);
                ArrayList<Object> death = (ArrayList<Object>) headers.get("x-death");
                assertNotNull(death);
                assertEquals(1, death.size());
                assertDeathReason(death, 0, TEST_QUEUE_NAME, reason,
                        "amq.direct",
                        Arrays.asList(new String[]{"test"}));
            }
        });
    }

    private void ttlTest(final long ttl) throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", ttl);
        deadLetterTest(new Runnable() {
                public void run() { sleep(ttl + 1500); }
            }, args, "expired");
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
    private void checkPromptArrival(QueueingConsumer c,
                                    int count, long latency) throws Exception {
        long epsilon = TTL / 50;
        for (int i = 0; i < count; i++) {
            Delivery d = c.nextDelivery(TTL + TTL + latency + epsilon);
            assertNotNull("message #" + i + " did not expire", d);
            long now = System.currentTimeMillis();
            long publishTime = Long.valueOf(new String(d.getBody()));
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
            args = new HashMap<String, Object>();
        }

        if (ttl > 0){
            args.put("x-message-ttl", ttl);
        }

        args.put(DLX_ARG, deadLetterExchange);
        if (deadLetterRoutingKey != null) {
            args.put(DLX_RK_ARG, deadLetterRoutingKey);
        }
        channel.queueDeclare(queue, false, true, false, args);
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

        List<String> deathRKs = new ArrayList<String>();
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

    private static interface WithResponse {
        static final WithResponse NULL = new WithResponse() {
                public void process(GetResponse getResponse) {
                }
            };

        public void process(GetResponse response);
    }
}
