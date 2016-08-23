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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class LogTail {
    public static void main(String[] args) {
        try {
            String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            String exchange = (args.length > 1) ? args[1] : "amq.rabbitmq.log";

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            Channel ch1 = conn.createChannel();

            String queueName = ch1.queueDeclare().getQueue();
            ch1.queueBind(queueName, exchange, "#");

            QueueingConsumer consumer = new QueueingConsumer(ch1);
            ch1.basicConsume(queueName, true, consumer);
            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                String routingKey = delivery.getEnvelope().getRoutingKey();
                String contentType = delivery.getProperties().getContentType();
                System.out.println("Content-type: " + contentType);
                System.out.println("Routing-key: " + routingKey);
                System.out.println("Body:");
                System.out.println(new String(delivery.getBody()));
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
