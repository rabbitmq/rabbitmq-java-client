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
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;

public class SimpleTopicConsumer {
    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 4) {
                System.err.print("Usage: SimpleTopicConsumer brokeruri [topicpattern\n" +
                                 "                                      [exchange\n" +
                                 "                                       [queue]]]\n" +
                                 "where\n" +
                                 " - topicpattern defaults to \"#\",\n" +
                                 " - exchange to \"amq.topic\", and\n" +
                                 " - queue to a private, autodelete queue\n");
                System.exit(1);
            }

            String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            String topicPattern = (args.length > 1) ? args[1] : "#";
            String exchange = (args.length > 2) ? args[2] : null;
            String queue = (args.length > 3) ? args[3] : null;

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            final Channel channel = conn.createChannel();

            if (exchange == null) {
                exchange = "amq.topic";
            } else {
                channel.exchangeDeclare(exchange, "topic");
            }

            if (queue == null) {
                queue = channel.queueDeclare().getQueue();
            } else {
              channel.queueDeclare(queue, false, false, false, null);
            }

            channel.queueBind(queue, exchange, topicPattern);

            System.out.println("Listening to exchange " + exchange + ", pattern " + topicPattern +
                               " from queue " + queue);

            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(queue, consumer);
            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                Envelope envelope = delivery.getEnvelope();
                System.out.println(envelope.getRoutingKey() + ": " + new String(delivery.getBody()));
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        } catch (Exception ex) {
            System.err.println("Main thread caught exception: " + ex);
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
