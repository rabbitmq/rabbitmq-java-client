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

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.functional.ClusteredTestBase;

import java.io.IOException;

/**
 * From bug 19844 - we want to be sure that publish vs everything else can't
 * happen out of order
 */
public class EffectVisibilityCrossNodeTest extends ClusteredTestBase {
    private static final String exchange = "exchange";

    private String[] queues = new String[QUEUES];

    @Override
    protected void createResources() throws IOException {
        channel.exchangeDeclare(exchange, "fanout");

        for (int i = 0; i < queues.length ; i++) {
            queues[i] = alternateChannel.queueDeclare().getQueue();
            alternateChannel.queueBind(queues[i], exchange, "");
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.exchangeDelete(exchange);
    }

    private static final int QUEUES = 5;
    private static final int COMMITS = 500;
    private static final int MESSAGES_PER_COMMIT = 10;

    public void testEffectVisibility() throws Exception {
        channel.txSelect();

        for (int i = 0; i < COMMITS; i++) {
            for (int j = 0; j < MESSAGES_PER_COMMIT; j++) {
                channel.basicPublish(exchange, "", MessageProperties.MINIMAL_BASIC, ("" + (i * MESSAGES_PER_COMMIT + j)).getBytes());
            }
            channel.txCommit();

            for (int j = 0; j < MESSAGES_PER_COMMIT; j++) {
                channel.basicPublish(exchange, "", MessageProperties.MINIMAL_BASIC, "bad".getBytes());
            }
            channel.txRollback();
        }

        for (int i = 0; i < queues.length ; i++) {
            QueueingConsumer consumer = new QueueingConsumer(alternateChannel);
            alternateChannel.basicConsume(queues[i], true, consumer);

            for (int j = 0; j < MESSAGES_PER_COMMIT * COMMITS; j++) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery(1000);
                assertNotNull(delivery);
                int sequence = Integer.parseInt(new String(delivery.getBody()));

                assertEquals(j, sequence);
            }
        }
    }
}
