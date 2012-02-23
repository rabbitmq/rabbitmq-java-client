package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
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
    private static final String DLX = "dead.letter.exchange";
    private static final String DLX_ARG = "x-dead-letter-exchange";
    private static final String DLX_RK_ARG = "x-dead-letter-routing-key";
    private static final String TEST_QUEUE_NAME = "test.queue.dead.letter";
    private static final String DLQ = "queue.dlq";
    private static final String DLQ2 = "queue.dlq2";
    private static final int MSG_COUNT = 10;
    private static final int MSG_COUNT_MANY = 1000;

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

    public void testDeadLetterEmpty() throws Exception {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null);
        channel.queuePurge(TEST_QUEUE_NAME);
        channel.queueDelete(TEST_QUEUE_NAME);
    }

    public void testDeadLetterQueueTTLExpiredMessages() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 1000);

        deadLetterTest(new Runnable() {
                public void run() {
                    sleep(2000);
                }
            }, args, "expired");
    }

    public void testDeadLetterExchangeDeleteTwice()
        throws IOException
    {
        declareQueue(TEST_QUEUE_NAME, DLX, null, null, 1);

        publishN(MSG_COUNT_MANY);
        channel.queueDelete(TEST_QUEUE_NAME);
        try {
            channel.queueDelete(TEST_QUEUE_NAME);
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
        consumeN(TEST_QUEUE_NAME, 0, new WithResponse() {
                public void process(GetResponse getResponse) {
                }
            });
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

        consumeN(DLQ, 0, new WithResponse() {
                public void process(GetResponse getResponse) {
                }
            });
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

        consumeN(DLQ, MSG_COUNT, new WithResponse() {
                @SuppressWarnings("unchecked")
                public void process(GetResponse getResponse) {
                    Map<String, Object> headers = getResponse.getProps().getHeaders();
                    assertNotNull(headers);
                    ArrayList<Object> death = (ArrayList<Object>)headers.get("x-death");
                    assertNotNull(death);
                    assertEquals(1, death.size());
                    assertDeathReason(death, 0, TEST_QUEUE_NAME, reason,
                                      "amq.direct",
                                      Arrays.asList(new String[]{"test"}));
                }
            });
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // whoosh
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
        for(int x = 0; x < n; x++) {
            channel.basicPublish("amq.direct", "test", props,
                                 "test message".getBytes());
        }
    }

    private void consumeN(String queue, int n, WithResponse withResponse)
        throws IOException
    {
        for(int x = 0; x < n; x++) {
            GetResponse getResponse =
                channel.basicGet(queue, true);
            assertNotNull("Message not dead-lettered", getResponse);
            assertEquals("test message", new String(getResponse.getBody()));
            withResponse.process(getResponse);
        }
        GetResponse getResponse = channel.basicGet(queue, true);
        assertNull("expected empty queue", getResponse);
    }

    @SuppressWarnings("unchecked")
    private void assertDeathReason(List<Object> death, int num,
                                   String queue, String reason,
                                   String exchange, List<String> routingKeys)
    {
        Map<String, Object> deathHeader =
            (Map<String, Object>)death.get(num);
        assertEquals(exchange, deathHeader.get("exchange").toString());

        List<String> deathRKs = new ArrayList<String>();
        for (Object rk : (ArrayList)deathHeader.get("routing-keys")) {
            deathRKs.add(rk.toString());
        }
        Collections.sort(deathRKs);
        Collections.sort(routingKeys);
        assertEquals(routingKeys, deathRKs);

        assertDeathReason(death, num, queue, reason);
    }

    @SuppressWarnings("unchecked")
    private void assertDeathReason(List<Object> death, int num,
                                   String queue, String reason) {
        Map<String, Object> deathHeader =
            (Map<String, Object>)death.get(num);
        assertEquals(queue, deathHeader.get("queue").toString());
        assertEquals(reason, deathHeader.get("reason").toString());
    }

    private static interface WithResponse {
        public void process(GetResponse response);
    }
}
