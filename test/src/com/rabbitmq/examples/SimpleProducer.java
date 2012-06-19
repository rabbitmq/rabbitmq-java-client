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

/**
 * Java Application to publish a simple string message to an exchange/routing-key.
 */
public class SimpleProducer {
    /**
     * @param args command-line parameters
     * <p>
     * Zero to four positional parameters:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> -
     * the AMQP uri to connect to the broker to use. Default <code>amqp://localhost</code>.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * <li><i>message</i> - the message to publish. Default is a time-of-day message.
     * </li>
     * <li><i>exchange</i> - the name of the exchange to publish to. Default is the default exchange ("").
     * </li>
     * <li><i>routing-key</i> - the routing-key of the publication. Default is "<code>SimpleQueue</code>".
     * </li>
     * </ul>
     * <p>
     * If <i>exchange</i> is the default exchange a non-durable, shared, non-autoDelete queue is declared with name <i>routing-key</i>.
     * </p>
     * <p>
     * The message is published with no (minimal) properties.
     * </p>
     */
    public static void main(String[] args) {
        try {
            String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            String message = (args.length > 1) ? args[1] :
                "the time is " + new java.util.Date().toString();
            String exchange = (args.length > 2) ? args[2] : "";
            String routingKey = (args.length > 3) ? args[3] : "SimpleQueue";

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();

            Channel ch = conn.createChannel();

            if (exchange.equals("")) {
                ch.queueDeclare(routingKey, false, false, false, null);
            }
            ch.basicPublish(exchange, routingKey, null, message.getBytes());
            ch.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
