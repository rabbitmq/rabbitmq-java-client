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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;

public class PerMessageTTL extends TTLHandling {

    protected Object sessionTTL;

    @Override
    protected void publish(String msg) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME,
                MessageProperties.TEXT_PLAIN
                        .builder()
                        .expiration(String.valueOf(sessionTTL))
                        .build());
    }

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        this.sessionTTL = ttlValue;
        return this.channel.queueDeclare(name, false, true, false, null);
    }

    public void testExpiryWhenConsumerIsLateToTheParty() throws Exception {
        declareAndBindQueue(500);

        publish(MSG[0]);
        this.sessionTTL = 100;
        publish(MSG[1]);

        Thread.sleep(200);

        QueueingConsumer c = new QueueingConsumer(channel);
        channel.basicConsume(TTL_QUEUE_NAME, c);

        assertNotNull("Message unexpectedly expired", c.nextDelivery(100));
        assertNull("Message should have been expired!!", c.nextDelivery(100));
    }

    public void testRestartingExpiry() throws Exception {
        final String expiryDelay = "2000";
        declareDurableQueue(TTL_QUEUE_NAME);
        bindQueue();
        channel.basicPublish(TTL_EXCHANGE, TTL_QUEUE_NAME,
                MessageProperties.MINIMAL_PERSISTENT_BASIC
                        .builder()
                        .expiration(expiryDelay)
                        .build(), new byte[]{});
        long expiryStartTime = System.currentTimeMillis();
        restart();
        Thread.sleep(Integer.parseInt(expiryDelay));
        try {
            assertNull("Message should have expired after broker restart", get());
        } finally {
            deleteQueue(TTL_QUEUE_NAME);
        }
    }

}
