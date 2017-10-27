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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

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

    @Test public void multipleTTLTypes() throws IOException {
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

    @Test public void invalidTypeUsedInTTL() throws Exception {
        try {
            declareAndBindQueue("foobar");
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using non-numeric values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void trailingCharsUsedInTTL() throws Exception {
        try {
            declareAndBindQueue("10000foobar");
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using non-numeric values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void tTLMustBePositive() throws Exception {
        try {
            declareAndBindQueue(-10);
            publishAndSync(MSG[0]);
            fail("Should not be able to set TTL using negative values");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void tTLAllowZero() throws Exception {
        try {
            declareQueue(0);
            publishAndSync(MSG[0]);
        } catch (IOException e) {
            fail("Should be able to set ttl to zero");
        }
    }

    @Test public void messagesExpireWhenUsingBasicGet() throws Exception {
        declareAndBindQueue(200);
        publish(MSG[0]);
        Thread.sleep(1000);

        String what = get();
        assertNull("expected message " + what + " to have been removed", what);
    }

    @Test public void publishAndGetWithExpiry() throws Exception {
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

    @Test public void transactionalPublishWithGet() throws Exception {
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

    @Test public void expiryWithRequeue() throws Exception {
        declareAndBindQueue(400);

        publish(MSG[0]);
        Thread.sleep(200);
        publish(MSG[1]);
        publish(MSG[2]);

        expectBodyAndRemainingMessages(MSG[0], 2);
        expectBodyAndRemainingMessages(MSG[1], 1);

        closeChannel();
        openChannel();

        Thread.sleep(300);
        expectBodyAndRemainingMessages(MSG[1], 1);
        expectBodyAndRemainingMessages(MSG[2], 0);
    }

    /*
    * Test expiry of re-queued messages after being consumed instantly
    */
    @Test public void expiryWithReQueueAfterConsume() throws Exception {
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

    @Test public void zeroTTLDelivery() throws Exception {
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
        assertNotNull(response);
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
