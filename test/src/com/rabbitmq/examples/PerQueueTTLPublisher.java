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
