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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 *
 */
public class PerQueueTTL extends BrokerTestCase {

    private static final String TTL_EXCHANGE           = "ttl.exchange";
    private static final String TTL_ARG                = "x-message-ttl";
    private static final String TTL_QUEUE_NAME         = "queue.ttl";
    private static final String TTL_INVALID_QUEUE_NAME = "invalid.queue.ttl";

    private static final String[] MSG = {"one", "two", "three"};

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(TTL_EXCHANGE, "direct");
    }

    @Override
    protected void releaseResources() throws IOException {
        this.channel.exchangeDelete(TTL_EXCHANGE);
    }

    public void testCreateQueueTTLTypes() throws IOException {
        Object[] args = { (byte)200, (short)200, 200, 200L };
        for (Object ttl : args) {
            try {
                declareQueue(ttl);
            } catch(IOException ex) {
                fail("Should be able to use " + ttl.getClass().getName() +
                     " for x-message-ttl");
            }
        }
    }

    public void testCreateQueueWithInvalidTTL() throws Exception {
        try {
            declareQueue(TTL_INVALID_QUEUE_NAME, "foobar");
            fail("Should not be able to declare a queue with a non-long value for x-message-ttl");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testTTLMustBeGtZero() throws Exception {
        try {
            declareQueue(TTL_INVALID_QUEUE_NAME, 0);
            fail("Should not be able to declare a queue with zero for x-message-ttl");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testTTLMustBePositive() throws Exception {
        try {
            declareQueue(TTL_INVALID_QUEUE_NAME, -10);
            fail("Should not be able to declare a queue with negative value for x-message-ttl");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testQueueRedeclareEquivalence() throws Exception {
        declareQueue(10);
        try {
            declareQueue(20);
            fail("Should not be able to redeclare with different x-message-ttl");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testQueueRedeclareSemanticEquivalence() throws Exception {
        declareQueue((byte)10);
        declareQueue(10);
        declareQueue((short)10);
        declareQueue(10L);
    }

    public void testQueueRedeclareSemanticNonEquivalence() throws Exception {
        declareQueue(10);
        try {
            declareQueue(10.0);
            fail("Should not be able to redeclare with x-message-ttl argument of different type");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    /*
     * Test messages expire when using basic get.
     */
    public void testPublishAndGetWithExpiry() throws Exception {
        declareAndBindQueue(200);

        publish(MSG[0]);
        Thread.sleep(150);

        publish(MSG[1]);
        Thread.sleep(100);

        publish(MSG[2]);

        assertEquals(MSG[1], get());
        assertEquals(MSG[2], get());

    }

    /*
     * Test get expiry for messages sent under a transaction
     */
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

    /*
     * Test expiry of requeued messages
     */
    public void testExpiryWithRequeue() throws Exception {
        declareAndBindQueue(100);

        publish(MSG[0]);
        Thread.sleep(50);
        publish(MSG[1]);
        publish(MSG[2]);

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
    public void testExpiryWithRequeueAfterConsume() throws Exception {
        declareAndBindQueue(100);
        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);

        publish(MSG[0]);
        assertNotNull(c.nextDelivery(100));

        closeChannel();
        Thread.sleep(150);
        openChannel();

        assertNull("Requeued message not expired", get());
    }

    private String get() throws IOException {
        GetResponse response = basicGet(TTL_QUEUE_NAME);
        return response == null ? null : new String(response.getBody());
    }

    private void publish(String msg) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    private void declareAndBindQueue(Object ttlValue) throws IOException {
        declareQueue(ttlValue);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);
    }

    private AMQP.Queue.DeclareOk declareQueue(Object ttlValue) throws IOException {
        return declareQueue(TTL_QUEUE_NAME, ttlValue);
    }

    private AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        Map<String, Object> argMap = Collections.singletonMap(TTL_ARG, ttlValue);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    private void expectBodyAndRemainingMessages(String body, int messagesLeft) throws IOException {
        GetResponse response = channel.basicGet(TTL_QUEUE_NAME, false);
        assertEquals(body, new String(response.getBody()));
        assertEquals(messagesLeft,  response.getMessageCount());
    }

}
