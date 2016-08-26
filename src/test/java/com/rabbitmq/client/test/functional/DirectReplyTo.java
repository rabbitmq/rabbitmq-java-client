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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

public class DirectReplyTo extends BrokerTestCase {
    private static final String QUEUE = "amq.rabbitmq.reply-to";

    @Test public void roundTrip() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        declare(connection, replyTo, true);
        channel.confirmSelect();
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);
        channel.waitForConfirms();

        QueueingConsumer.Delivery del = c.nextDelivery();
        assertEquals("response", new String(del.getBody()));
    }

    @Test public void hack() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String replyTo = rpcFirstHalf(c);
        // 5 chars should overwrite part of the key but not the pid; aiming to prove
        // we can't publish using just the pid
        replyTo = replyTo.substring(0, replyTo.length() - 5) + "xxxxx";
        declare(connection, replyTo, false);
        basicPublishVolatile("response".getBytes(), "", replyTo, MessageProperties.BASIC);

        QueueingConsumer.Delivery del = c.nextDelivery(500);
        assertNull(del);
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

    @Test public void consumeFail() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
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

    @Test public void consumeSuccess() throws IOException, InterruptedException {
        QueueingConsumer c = new QueueingConsumer(channel);
        String ctag = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag);

        String ctag2 = channel.basicConsume(QUEUE, true, c);
        channel.basicCancel(ctag2);
        assertNotSame(ctag, ctag2);
    }

    private String rpcFirstHalf(QueueingConsumer c) throws IOException {
        channel.basicConsume(QUEUE, true, c);
        String serverQueue = channel.queueDeclare().getQueue();
        basicPublishVolatile("request".getBytes(), "", serverQueue, props());

        GetResponse req = channel.basicGet(serverQueue, true);
        return req.getProps().getReplyTo();
    }

    private AMQP.BasicProperties props() {
        return MessageProperties.BASIC.builder().replyTo(QUEUE).build();
    }
}
