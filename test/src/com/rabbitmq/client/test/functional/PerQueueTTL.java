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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 *
 */
public class PerQueueTTL extends BrokerTestCase {

    private static final String TTL_EXCHANGE = "ttl.exchange";

    private static final String TTL_ARG = "x-message-ttl";

    private static final String TTL_QUEUE_NAME = "queue.ttl";

    private static final String TTL_INVALID_QUEUE_NAME = "invalid.queue.ttl";

    @Override
    protected void createResources() throws IOException {
        this.channel.exchangeDeclare(TTL_EXCHANGE, "direct");
    }

    @Override
    protected void releaseResources() throws IOException {
        this.channel.exchangeDelete(TTL_EXCHANGE);
    }

    public void testCreateQueueWithByteTTL() throws IOException {
        try {
            declareQueue(TTL_QUEUE_NAME, (byte)200);
        }   catch(IOException ex) {
            fail("Should be able to use byte for queue TTL");
        }
    }
    public void testCreateQueueWithShortTTL() throws IOException {
        try {
            declareQueue(TTL_QUEUE_NAME, (short)200);
        }   catch(IOException ex) {
            fail("Should be able to use short for queue TTL");
        }
    }
    public void testCreateQueueWithIntTTL() throws IOException {
        try {
            declareQueue(TTL_QUEUE_NAME, 200);
        }   catch(IOException ex) {
            fail("Should be able to use int for queue TTL");
        }
    }

    public void testCreateQueueWithLongTTL() throws IOException {
        try {
            declareQueue(TTL_QUEUE_NAME, 200L);
        }   catch(IOException ex) {
            fail("Should be able to use long for queue TTL");
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
            fail("Should not be able to declare a queue with zero for x-message-ttl");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testQueueRedeclareEquivalence() throws Exception {
        declareQueue(TTL_QUEUE_NAME, 10);
        try {
            declareQueue(TTL_QUEUE_NAME, 20);
            fail("Should not be able to redeclare with different TTL");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testQueueRedeclareSemanticEquivalence() throws Exception {
        declareQueue(TTL_QUEUE_NAME, (byte)10);
        declareQueue(TTL_QUEUE_NAME, 10);
        declareQueue(TTL_QUEUE_NAME, (short)10);
        declareQueue(TTL_QUEUE_NAME, 10L);
    }

    public void testQueueRedeclareSemanticNonEquivalence() throws Exception {
        declareQueue(TTL_QUEUE_NAME, 10);
        try {
            declareQueue(TTL_QUEUE_NAME, 10.0);
            fail("Should not be able to redeclare with argument of different type");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    /*
     * Test messages expire when using basic get.
     */
    public void testPublishAndGetWithExpiry() throws Exception {
        long ttl = 2000;
        declareQueue(TTL_QUEUE_NAME, ttl);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);

        byte[] msg1 = "one".getBytes();
        byte[] msg2 = "two".getBytes();
        byte[] msg3 = "three".getBytes();

        basicPublishVolatile(msg1, TTL_EXCHANGE, TTL_QUEUE_NAME);
        Thread.sleep(1500);

        basicPublishVolatile(msg2, TTL_EXCHANGE, TTL_QUEUE_NAME);
        Thread.sleep(1000);

        basicPublishVolatile(msg3, TTL_EXCHANGE, TTL_QUEUE_NAME);

        assertEquals("two", new String(get()));
        assertEquals("three", new String(get()));

    }
    
    /*
     * Test get expiry for messages sent under a transaction
     */
    public void testTransactionalPublishWithGet() throws Exception {
        long ttl = 1000;
        declareQueue(TTL_QUEUE_NAME, ttl);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);

        byte[] msg1 = "one".getBytes();
        byte[] msg2 = "two".getBytes();

        this.channel.txSelect();

        basicPublishVolatile(msg1, TTL_EXCHANGE, TTL_QUEUE_NAME);
        Thread.sleep(1500);

        basicPublishVolatile(msg2, TTL_EXCHANGE, TTL_QUEUE_NAME);
        this.channel.txCommit();
        Thread.sleep(500);

        assertEquals("one", new String(get()));
        Thread.sleep(800);

        assertNull(get());
    }

    /*
     * Test expiry of requeued messages
     */
    public void testExpiryWithRequeue() throws Exception {
        long ttl = 1000;
        declareQueue(TTL_QUEUE_NAME, ttl);
        this.channel.queueBind(TTL_QUEUE_NAME, TTL_EXCHANGE, TTL_QUEUE_NAME);

        byte[] msg1 = "one".getBytes();
        byte[] msg2 = "two".getBytes();
        byte[] msg3 = "three".getBytes();

        basicPublishVolatile(msg1, TTL_EXCHANGE, TTL_QUEUE_NAME);
        Thread.sleep(500);
        basicPublishVolatile(msg2, TTL_EXCHANGE, TTL_QUEUE_NAME);
        basicPublishVolatile(msg3, TTL_EXCHANGE, TTL_QUEUE_NAME);

        expectBodyAndRemainingMessages("one", 2);
        expectBodyAndRemainingMessages("two", 1);

        closeChannel();
        openChannel();

        Thread.sleep(600);
        expectBodyAndRemainingMessages("two", 1);
        expectBodyAndRemainingMessages("three", 0);
    }


    private byte[] get() throws IOException {
        GetResponse response = basicGet(TTL_QUEUE_NAME);
        if(response == null) {
            return null;
        }
        return response.getBody();
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
