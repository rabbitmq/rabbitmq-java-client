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


package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class SimpleTopicProducer {
    public static final String DEFAULT_TOPIC = "one.two.three.four";

    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 5) {
                System.err.print("Usage: SimpleTopicProducer brokerhostname [brokerport\n" +
                                 "                                           [topic\n" +
                                 "                                            [exchange\n" +
                                 "                                             [message]]]]\n" +
                                 "where\n" +
                                 " - topic defaults to \""+DEFAULT_TOPIC+"\",\n" +
                                 " - exchange defaults to \"amq.topic\", and\n" +
                                 " - message defaults to a time-of-day message\n");
                System.exit(1);
            }
            String hostName = (args.length > 0) ? args[0] : "localhost";
            int portNumber = (args.length > 1) ? Integer.parseInt(args[1]) : AMQP.PROTOCOL.PORT;
            String topic = (args.length > 2) ? args[2] : DEFAULT_TOPIC;
            String exchange = (args.length > 3) ? args[3] : null;
            String message = (args.length > 4) ? args[4] :
                "the time is " + new java.util.Date().toString();

            ConnectionFactory cfconn = new ConnectionFactory(); 
            cfconn.setHost(hostName); 
            cfconn.setPort(portNumber);
            Connection conn = cfconn.newConnection();

            Channel ch = conn.createChannel();

            if (exchange == null) {
                exchange = "amq.topic";
            } else {
                ch.exchangeDeclare(exchange, "topic");
            }

            System.out.println("Sending to exchange " + exchange + ", topic " + topic);
            ch.basicPublish(exchange, topic, null, message.getBytes());
            ch.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
