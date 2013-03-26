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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.examples;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class SimpleConsumer {
    public static void main(String[] args) {
        try {
            String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            String queueName = (args.length > 1) ? args[1] : "SimpleQueue";

            ConnectionFactory connFactory = new ConnectionFactory();
            connFactory.setUri(uri);
            Connection conn = connFactory.newConnection();

            final Channel ch = conn.createChannel();

            ch.queueDeclare(queueName, false, false, false, null);

            QueueingConsumer consumer = new QueueingConsumer(ch);
            ch.basicConsume(queueName, consumer);
            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                System.out.println("Message: " + new String(delivery.getBody()));
                ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        } catch (Exception ex) {
            System.err.println("Main thread caught exception: " + ex);
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
