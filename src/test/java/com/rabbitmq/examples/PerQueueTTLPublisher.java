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

package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Collections;

/**
 */
public class PerQueueTTLPublisher {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String exchange = "ttl.exchange";
        String queue = "ttl.queue";

        // exchange
        channel.exchangeDeclare(exchange, "direct");

        // queue
        channel.queueDeclare(queue, true, false, false, Collections.singletonMap("x-message-ttl", (Object) 30000L));
        channel.queueBind(queue, exchange, queue, null);

        // send a message
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
        for(int x = 0; x < 10; x++) {
            channel.basicPublish(exchange, queue, props, ("Msg [" + x + "]").getBytes());
        }

        System.out.println("Done");
        channel.close();
        connection.close();
    }
}
