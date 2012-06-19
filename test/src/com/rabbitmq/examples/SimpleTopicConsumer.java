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

package com.rabbitmq.examples;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;

/**
 * Java Application to consume messages, matching a topic-pattern, published to a topic exchange.
 * <p>
 * Messages are consumed and printed until the application is cancelled.
 * </p>
 */
public class SimpleTopicConsumer {

    private static final String DEFAULT_TOPIC_EXCHANGE = "amq.topic";
    private static final String DEFAULT_TOPIC_PATTERN = "#";

    /**
     * @param args command-line parameters
     * <p>
     * One to four positional parameters:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> -
     * the AMQP uri to connect to the broker to use.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * <li><i>topic-pattern</i> - the topic pattern to listen for.
     * Default "<code>#</code>", meaning all topics.
     * </li>
     * <li><i>exchange</i> - the name of a topic exchange to consume from.
     * Default "<code>amq.topic</code>".
     * </li>
     * <li><i>queue-name</i> - the name of a queue to consume from.
     * Default is to create an anonymous, private, autoDelete queue. If <i>queue-name</i> is given
     * a named, shared, non-autoDelete queue is declared.
     * </li>
     * </ul>
     */
    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 4) {
                System.err.print("Usage: SimpleTopicConsumer brokeruri [topicpattern\n" +
                                 "                                      [exchange\n" +
                                 "                                       [queue]]]\n" +
                                 "where\n" +
                                 " - topicpattern defaults to \"" + DEFAULT_TOPIC_PATTERN + "\",\n" +
                                 " - exchange to \"" + DEFAULT_TOPIC_EXCHANGE + "\", and\n" +
                                 " - queue to a private, autoDelete queue\n");
                System.exit(1);
            }

            String uri = args[0];
            String topicPattern = (args.length > 1) ? args[1] : DEFAULT_TOPIC_PATTERN;
            String exchange = (args.length > 2) ? args[2] : DEFAULT_TOPIC_EXCHANGE;
            String queue = (args.length > 3) ? args[3] : null;

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            final Channel channel = conn.createChannel();

            if (!DEFAULT_TOPIC_EXCHANGE.equals(exchange)) {
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
