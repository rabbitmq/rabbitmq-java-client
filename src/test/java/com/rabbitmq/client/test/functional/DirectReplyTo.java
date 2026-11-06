// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.*;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.test.BrokerTestCase;

public class DirectReplyTo extends BrokerTestCase {
    private static final String QUEUE = "amq.rabbitmq.reply-to";

    @Test public void roundTrip() throws IOException, InterruptedException {
        QueueMessageConsumer c = new QueueMessageConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        declare(connection, replyTo, true);
        channel.confirmSelect();
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);
        channel.waitForConfirms();

        byte[] body = c.nextDelivery(10000);
        assertEquals("response", new String(body));
    }

    @Test public void hack() throws IOException, InterruptedException, TimeoutException {
        QueueMessageConsumer c = new QueueMessageConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        // 5 chars should overwrite part of the key but not the pid; aiming to prove
        // we can't publish using just the pid
        replyTo = replyTo.substring(0, replyTo.length() - 5) + "xxxxx";
        expectDoesNotExist(connection, replyTo);
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);

        byte[] body = c.nextDelivery(500);
        assertNull(body);
    }

    private void declare(Connection connection, String q, boolean expectedExists) throws IOException {
        Channel ch = connection.createChannel();
        try {
            ch.queueDeclarePassive(q);
            assertTrue(expectedExists);
        } catch (IOException e) {
            assertFalse(expectedExists);
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            // Hmmm...
            channel = connection.createChannel();
        }
    }

    private void expectDoesNotExist(Connection connection, String q) throws IOException, TimeoutException {
        // hack after a broker regression introduced in https://github.com/rabbitmq/rabbitmq-server/pull/18034
        Channel ch = connection.createChannel();
        try {
            ch.queueDeclarePassive(q);
        } catch (IOException e) {
            Connection c = null;
            if (connection.isOpen()) {
                checkShutdownSignal(AMQP.NOT_FOUND, e);
                c = connection;
            } else {
                checkShutdownSignal(AMQP.INTERNAL_ERROR, e);
                this.connection = null;
                openConnection();
                c = this.connection;
            }
            channel = c.createChannel();
        }
    }

    @Test public void consumeFail() throws IOException, InterruptedException {
        DefaultConsumer c = new DefaultConsumer(channel);
        Channel ch = connection.createChannel();
        try {
            ch.basicConsume(QUEUE, false, c);
        } catch (IOException e) {
            // Can't have ack mode
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }

        ch = connection.createChannel();
        ch.basicConsume(QUEUE, true, c);
        try {
            ch.basicConsume(QUEUE, true, c);
        } catch (IOException e) {
            // Can't have multiple consumers
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    @Test public void consumeSuccess() throws IOException {
        DefaultConsumer c = new DefaultConsumer(channel);
        String ctag = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag);

        String ctag2 = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag2);
        assertNotSame(ctag, ctag2);
    }

    private String rpcFirstHalf(Consumer c) throws IOException {
        channel.basicConsume(QUEUE, true, c);
        String serverQueue = channel.queueDeclare().getQueue();
        basicPublishVolatile("request".getBytes(), "", serverQueue, props());

        GetResponse req = channel.basicGet(serverQueue, true);
        return req.getProps().getReplyTo();
    }

    private AMQP.BasicProperties props() {
        return MessageProperties.BASIC.builder().replyTo(QUEUE).build();
    }

    class QueueMessageConsumer extends DefaultConsumer {

        BlockingQueue<byte[]> messages = new LinkedBlockingQueue<byte[]>();

        public QueueMessageConsumer(Channel channel) {
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
