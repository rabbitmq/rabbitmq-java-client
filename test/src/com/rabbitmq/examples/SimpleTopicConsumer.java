//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;

public class SimpleTopicConsumer {
    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 5) {
                System.err.print("Usage: SimpleTopicConsumer brokerhostname [brokerport\n" +
                                 "                                           [topicpattern\n" +
                                 "                                            [exchange\n" +
                                 "                                             [queue]]]]\n" +
                                 "where\n" +
                                 " - topicpattern defaults to \"#\",\n" +
                                 " - exchange to \"amq.topic\", and\n" +
                                 " - queue to a private, autodelete queue\n");
                System.exit(1);
            }

            String hostName = (args.length > 0) ? args[0] : "localhost";
            int portNumber = (args.length > 1) ? Integer.parseInt(args[1]) : AMQP.PROTOCOL.PORT;
            String topicPattern = (args.length > 2) ? args[2] : "#";
            String exchange = (args.length > 3) ? args[3] : null;
            String queue = (args.length > 4) ? args[4] : null;

            Connection conn = new ConnectionFactory().newConnection(hostName, portNumber);

            final Channel channel = conn.createChannel();

            if (exchange == null) {
                exchange = "amq.topic";
            } else {
                channel.exchangeDeclare(exchange, "topic");
            }

            if (queue == null) {
                queue = channel.queueDeclare().getQueue();
            } else {
                channel.queueDeclare(queue);
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
