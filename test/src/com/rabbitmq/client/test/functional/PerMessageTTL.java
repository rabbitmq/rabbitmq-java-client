package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;

import java.io.IOException;

public class PerMessageTTL extends TTLHandling {

    @Override
    protected void createResources() throws IOException {
        super.createResources();
        declareAndBindQueue();
        this.channel.confirmSelect();
    }

    @Override
    protected void releaseResources() throws IOException {
        // this.channel.queueUnbind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);
        // this.channel.queueDelete(TTL_QUEUE_NAME);
        super.releaseResources();
    }

    public void testSupportedTTLTypes() throws IOException {
        Object[] args = { (byte)200, (short)200, 200, 200L };
        for (Object ttl : args) {
            try {
                publish(MSG[0], ttl);
                this.channel.waitForConfirmsOrDie();
            } catch(Exception ex) {
                fail("Should be able to use " + ttl.getClass().getName() +
                        " for basic.expiration: " + ex.getMessage());
            }
        }
    }

    public void testTTLAllowZero() throws Exception {
        try {
            publish(MSG[0], (byte) 0);
            this.channel.waitForConfirmsOrDie();
        } catch (Exception e) {
            fail("Should be able to publish with basic.expiration set to zero: " +
                    e.getMessage());
        }
    }

    public void testPublishWithInvalidTTL() throws InterruptedException, IOException {
        try {
            publishAndAwaitConfirms(MSG[0], "foobar");
            fail("Should not be able to set a non-long value for basic.expiration");
        } catch (ShutdownSignalException e) {
            checkShutdownSignal(AMQP.INTERNAL_ERROR, e);
        }
    }

    public void testTTLMustBePositive() throws Exception {
        try {
            publishAndAwaitConfirms(MSG[0], -15);
            fail("Should not be able to set a negative value for basic.expiration");
        } catch (ShutdownSignalException e) {
            checkShutdownSignal(AMQP.INTERNAL_ERROR, e);
        }
    }

    /*
     * Test messages expire when using basic get.
     */
    public void testPublishAndGetWithExpiry() throws Exception {
        // this seems quite timing dependent - would it not be better
        // to test this by setting up a DLX and verifying that the
        // expired messages have been sent there instead?
        publish(MSG[0], 200);
        Thread.sleep(150);

        publish(MSG[1], 200);
        Thread.sleep(100);

        publish(MSG[2], 200);

        assertEquals(MSG[1], get());
        assertEquals(MSG[2], get());
        assertNull(get());
    }

    /*
     * Test get expiry for messages sent under a transaction
     */
    public void testTransactionalPublishWithGet() throws Exception {
        closeChannel();
        openChannel();
        this.channel.txSelect();

        publish(MSG[0], 100);
        Thread.sleep(150);

        publish(MSG[1], 100);
        this.channel.txCommit();
        Thread.sleep(50);

        assertEquals(MSG[0], get());
        Thread.sleep(80);

        assertNull(get());
    }

    /*
     * Test expiry of requeued messages
     */
    public void testExpiryWithReQueue() throws Exception {
        publish(MSG[0], 100);
        Thread.sleep(50);

        publish(MSG[1], 100);
        publish(MSG[2], 100);

        expectBodyAndRemainingMessages(MSG[0], 2);
        expectBodyAndRemainingMessages(MSG[1], 1);

        closeChannel();
        openChannel();

        Thread.sleep(60);
        expectBodyAndRemainingMessages(MSG[1], 1);
        expectBodyAndRemainingMessages(MSG[2], 0);
    }

    /*
     * Test expiry of requeued messages after being consumed instantly
     */
    public void testExpiryWithReQueueAfterConsume() throws Exception {
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);

        publish(MSG[0], 100);
        assertNotNull(c.nextDelivery(100));

        closeChannel();
        Thread.sleep(150);
        openChannel();

        assertNull("Re-queued message not expired", get());
    }

    public void testZeroTTLDelivery() throws Exception {
        // when there is no consumer, message should expire
        publish(MSG[0], 0);
        assertNull(get());

        // when there is a consumer, message should be delivered
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);
        publish(MSG[0], 0);
        QueueingConsumer.Delivery d = c.nextDelivery(100);
        assertNotNull(d);

        // requeued messages should expire
        channel.basicReject(d.getEnvelope().getDeliveryTag(), true);
        assertNull(c.nextDelivery(100));
    }

    private void publishAndAwaitConfirms(String msg, Object expiration)
            throws IOException, InterruptedException {
        publish(msg, expiration);
        this.channel.waitForConfirmsOrDie();
    }

    private void publish(String msg, Object exp) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME,
                MessageProperties.TEXT_PLAIN
                        .builder()
                        .expiration(String.valueOf(exp))
                        .build());
    }

    private void declareAndBindQueue() throws IOException {
        declareQueue(TTL_QUEUE_NAME);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    private AMQP.Queue.DeclareOk declareQueue(String name) throws IOException {
        return this.channel.queueDeclare(name, false, true, false, null);
    }

}
