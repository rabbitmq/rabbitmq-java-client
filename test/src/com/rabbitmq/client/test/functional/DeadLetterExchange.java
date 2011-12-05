package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DeadLetterExchange extends BrokerTestCase {
    private static final String DLX = "dead.letter.exchange";
    private static final String DLX_ARG = "x-dead-letter-exchange";
    private static final String TEST_QUEUE_NAME = "test.queue.dead.letter";
    private static final String DLQ = "queue.dlq";
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

    public void testDeclareQueueWithNoDeadLetterExchange()
        throws IOException
    {
        channel.queueDeclare(TEST_QUEUE_NAME, false, true, false, null);
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
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }

    }

    public void testDeadLetterQueueTTLExpiredMessages() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 1000);

        deadLetterTest(new Runnable() {
                public void run() {
                    sleep(2000);
                }
            }, args, PropertiesFactory.NULL, "expired");
    }

    public void testDeadLetterQueueDeleted() throws Exception {
        deadLetterTest(new Callable<Void>() {
                public Void call() throws Exception{
                    channel.queueDelete(TEST_QUEUE_NAME);
                    return null;
                }
            }, null, PropertiesFactory.NULL, "queue_deleted");
    }

    public void testDeadLetterQueuePurged() throws Exception {
        deadLetterTest(new Callable<Void>() {
                public Void call() throws Exception{
                    channel.queuePurge(TEST_QUEUE_NAME);
                    return null;
                }
            }, null, PropertiesFactory.NULL, "queue_purged");
    }

    public void testDeadLetterQueueLeaseExpire() throws Exception {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 1000);

        deadLetterTest(new Runnable() {
                public void run() {
                    sleep(2000);
                }
            }, args, PropertiesFactory.NULL, "queue_deleted");
    }

    public void testDeadLetterOnReject() throws Exception {
        deadLetterTest(new Callable<Void>() {
                public Void call() throws Exception {
                    for (int x = 0; x < MSG_COUNT; x++) {
                        GetResponse getResponse =
                            channel.basicGet(TEST_QUEUE_NAME, false);
                        long tag = getResponse.getEnvelope().getDeliveryTag();
                        channel.basicReject(tag, false);
                    }
                    return null;
                }
            }, null, PropertiesFactory.NULL, "rejected");
    }

    public void testDeadLetterOnNack() throws Exception {
        deadLetterTest(new Callable<Void>() {
                public Void call() throws Exception {
                    for (int x = 0; x < MSG_COUNT; x++) {
                        GetResponse getResponse =
                            channel.basicGet(TEST_QUEUE_NAME, false);
                        long tag = getResponse.getEnvelope().getDeliveryTag();
                        channel.basicNack(tag, false, false);
                    }
                    return null;
                }
            }, null, PropertiesFactory.NULL, "rejected");
    }

    public void testDeadLetterNoDeadLetterQueue() throws IOException {
        channel.queueDelete(DLQ);
        declareQueue(DLX);
        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        publishN(MSG_COUNT, PropertiesFactory.NULL);
        channel.queuePurge(TEST_QUEUE_NAME);
    }

    public void testDeadLetterMultipleDeadLetterQueues()
        throws IOException
    {
        declareQueue(DLX);

        final String DLQ2 = "queue.dlq2";
        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test");

        publishN(MSG_COUNT, PropertiesFactory.NULL);
        channel.queuePurge(TEST_QUEUE_NAME);
    }

    public void testDeadLetterTwice() throws Exception {
        channel.queueDelete(DLQ);
        declareQueue(DLQ, DLX, null);

        declareQueue(DLX);

        final String DLQ2 = "queue.dle2";
        channel.queueDeclare(DLQ2, false, true, false, null);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");
        channel.queueBind(DLQ2, DLX, "test");

        publishN(MSG_COUNT, PropertiesFactory.NULL);

        channel.queuePurge(TEST_QUEUE_NAME);
        sleep(100);
        channel.queueDelete(DLQ);

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
                        assertDeathReason(death, 0, TEST_QUEUE_NAME, "queue_purged");
                    } else if (death.size() == 2) {
                        assertDeathReason(death, 0, DLQ, "queue_deleted");
                        assertDeathReason(death, 1, TEST_QUEUE_NAME, "queue_purged");
                    } else {
                        fail("message was dead-lettered more times than expected");
                    }
                }
            });
    }

    public void testDeadLetterSelf() throws Exception {
        channel.queueDelete(DLQ);
        declareQueue(DLQ, DLX, null);

        declareQueue(DLX);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        publishN(MSG_COUNT_MANY, PropertiesFactory.NULL);

        channel.queuePurge(TEST_QUEUE_NAME);
        sleep(100);
        channel.queuePurge(DLQ);

        // The messages have been purged once from TEST_QUEUE_NAME and
        // once from DLQ.
        consumeN(DLQ, MSG_COUNT_MANY, new WithResponse() {
                @SuppressWarnings("unchecked")
                public void process(GetResponse getResponse) {
                    Map<String, Object> headers = getResponse.getProps().getHeaders();
                    assertNotNull(headers);
                    ArrayList<Object> death = (ArrayList<Object>)headers.get("x-death");
                    assertNotNull(death);
                    assertEquals(2, death.size());
                    assertDeathReason(death, 0, DLQ, "queue_purged");
                    assertDeathReason(death, 1, TEST_QUEUE_NAME, "queue_purged");
                }
            });
    }

    private void deadLetterTest(final Runnable deathTrigger,
                                Map<String, Object> queueDeclareArgs,
                                PropertiesFactory propsFactory,
                                String reason)
        throws Exception
    {
        deadLetterTest(new Callable<Object>() {
                public Object call() throws Exception {
                    deathTrigger.run();
                    return null;
                }
            }, queueDeclareArgs, propsFactory, reason);
    }

    private void deadLetterTest(Callable<?> deathTrigger,
                                Map<String, Object> queueDeclareArgs,
                                PropertiesFactory propsFactory,
                                final String reason)
        throws Exception
    {
        declareQueue(TEST_QUEUE_NAME, DLX, queueDeclareArgs);

        channel.queueBind(TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DLQ, DLX, "test");

        publishN(MSG_COUNT, propsFactory);

        deathTrigger.call();

        consumeN(DLQ, MSG_COUNT, new WithResponse() {
                @SuppressWarnings("unchecked")
                public void process(GetResponse getResponse) {
                    Map<String, Object> headers = getResponse.getProps().getHeaders();
                    assertNotNull(headers);
                    ArrayList<Object> death = (ArrayList<Object>)headers.get("x-death");
                    assertNotNull(death);
                    assertEquals(1, death.size());
                    assertDeathReason(death, 0, TEST_QUEUE_NAME, reason);
                }
            });
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch(InterruptedException ex) {
            // whoosh
        }
    }

    private void declareQueue(Object deadLetterExchange) throws IOException {
        declareQueue(TEST_QUEUE_NAME, deadLetterExchange, null);
    }

    private void declareQueue(String queue, Object deadLetterExchange,
                              Map<String, Object> args) throws IOException {
        if (args == null) {
            args = new HashMap<String, Object>();
        }

        args.put(DLX_ARG, deadLetterExchange);
        channel.queueDeclare(queue, false, true, false, args);
    }

    private void publishN(int n, PropertiesFactory propsFactory)
        throws IOException
    {
        for(int x = 0; x < n; x++) {
            channel.basicPublish("amq.direct", "test",
                                 propsFactory.create(x),
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
                                   String queue, String reason) {
        Map<String, Object> deathHeader =
            (Map<String, Object>)death.get(num);
        assertEquals(queue,
                     deathHeader.get("queue").toString());
        assertEquals(reason, deathHeader.get("reason").toString());
    }

    private static interface PropertiesFactory {
        static final PropertiesFactory NULL = new PropertiesFactory(){
                public AMQP.BasicProperties create(int msgNum) {
                    return null;
                }
            };

        AMQP.BasicProperties create(int msgNum);
    }

    private static interface WithResponse {
        public void process(GetResponse response);
    }
}
