package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public abstract class TTLHandling extends BrokerTestCase {

    protected static final String TTL_EXCHANGE           = "ttl.exchange";
    protected static final String TTL_QUEUE_NAME         = "queue.ttl";
    protected static final String TTL_INVALID_QUEUE_NAME = "invalid.queue.ttl";

    protected static final String[] MSG = {"one", "two", "three"};

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(TTL_EXCHANGE, "direct");
    }

    @Override
    protected void releaseResources() throws IOException {
        this.channel.exchangeDelete(TTL_EXCHANGE);
    }

    public void testMultipleTTLTypes() throws IOException {
        final Object[] args = { (((byte)200) & (0xff)), (short)200, 200, 200L };
        for (Object ttl : args) {
            try {
                declareAndBindQueue(ttl);
                publishAndSync(MSG[0]);
            } catch(IOException ex) {
                fail("Should be able to use " + ttl.getClass().getName() +
                        " when setting TTL");
            }
        }
    }

    public void testInvalidTypeUsedInTTL() throws Exception {
        try {
            declareAndBindQueue("foobar");
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using non-numeric values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testTrailingCharsUsedInTTL() throws Exception {
        try {
            declareAndBindQueue("10000foobar");
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using non-numeric values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testTTLMustBePositive() throws Exception {
        try {
            declareAndBindQueue(-10);
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using negative values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testTTLAllowZero() throws Exception {
        try {
            declareQueue(0);
            publishAndSync(MSG[0]);
        } catch (IOException e) {
            fail("Should be able to set ttl to zero");
        }
    }

    public void testMessagesExpireWhenUsingBasicGet() throws Exception {
        declareAndBindQueue(200);
        publish(MSG[0]);
        Thread.sleep(1000);

        String what = get();
        assertNull("expected message " + what + " to have been removed", what);
    }

    public void testPublishAndGetWithExpiry() throws Exception {
        declareAndBindQueue(200);

        publish(MSG[0]);
        Thread.sleep(150);

        publish(MSG[1]);
        Thread.sleep(100);

        publish(MSG[2]);

        assertEquals(MSG[1], get());
        assertEquals(MSG[2], get());
        assertNull(get());
    }

    public void testTransactionalPublishWithGet() throws Exception {
        declareAndBindQueue(100);

        this.channel.txSelect();

        publish(MSG[0]);
        Thread.sleep(150);

        publish(MSG[1]);
        this.channel.txCommit();
        Thread.sleep(50);

        assertEquals(MSG[0], get());
        Thread.sleep(80);

        assertNull(get());
    }

    public void testExpiryWithRequeue() throws Exception {
        declareAndBindQueue(200);

        publish(MSG[0]);
        Thread.sleep(100);
        publish(MSG[1]);
        publish(MSG[2]);

        expectBodyAndRemainingMessages(MSG[0], 2);
        expectBodyAndRemainingMessages(MSG[1], 1);

        closeChannel();
        openChannel();

        Thread.sleep(150);
        expectBodyAndRemainingMessages(MSG[1], 1);
        expectBodyAndRemainingMessages(MSG[2], 0);
    }

    /*
    * Test expiry of re-queued messages after being consumed instantly
    */
    public void testExpiryWithReQueueAfterConsume() throws Exception {
        declareAndBindQueue(100);
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);

        publish(MSG[0]);
        assertNotNull(c.nextDelivery(100));

        closeChannel();
        Thread.sleep(150);
        openChannel();

        assertNull("Re-queued message not expired", get());
    }

    public void testZeroTTLDelivery() throws Exception {
        declareAndBindQueue(0);

        // when there is no consumer, message should expire
        publish(MSG[0]);
        assertNull(get());

        // when there is a consumer, message should be delivered
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);
        publish(MSG[0]);
        QueueingConsumer.Delivery d = c.nextDelivery(100);
        assertNotNull(d);

        // requeued messages should expire
        channel.basicReject(d.getEnvelope().getDeliveryTag(), true);
        assertNull(c.nextDelivery(100));
    }

    protected void expectBodyAndRemainingMessages(String body, int messagesLeft) throws IOException {
        GetResponse response = channel.basicGet(TTL_QUEUE_NAME, false);
        assertEquals(body, new String(response.getBody()));
        assertEquals(messagesLeft, response.getMessageCount());
    }

    protected void declareAndBindQueue(Object ttlValue) throws IOException {
        declareQueue(ttlValue);
        bindQueue();
    }

    protected void bindQueue() throws IOException {
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    protected AMQP.Queue.DeclareOk declareQueue(Object ttlValue) throws IOException {
        return declareQueue(TTL_QUEUE_NAME, ttlValue);
    }

    protected abstract AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException;

    protected String get() throws IOException {
        GetResponse response = basicGet(TTL_QUEUE_NAME);
        return response == null ? null : new String(response.getBody());
    }

    protected void publish(final String msg) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    protected void publishAndSync(String msg) throws IOException {
        publish(msg);
        this.channel.basicQos(0);
    }

}
