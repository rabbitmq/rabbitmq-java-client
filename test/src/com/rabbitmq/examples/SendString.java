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
 * Java Application to publish (the bytes of) a string to a given exchange, on a given broker, with a given routing key.
 */
public class SendString {
    /**
     * @param args command-line parameters:
     * <p>
     * Precisely five parameters:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> - the AMQP uri broker connection string.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * <li><i>exchange-name</i> - the exchange to publish to.
     * </li>
     * <li><i>exchange-type</i> - the type of exchange.
     * </li>
     * <li><i>routing-key</i> - the routing key to use.
     * </li>
     * <li><i>message</i> - the message string to publish.
     * </li>
     * </ul>
     * <p>
     * There are no defaults. Parameters after the fifth are ignored.
     * If fewer than five parameters are supplied a usage description is printed.
     * </p>
     */
    public static void main(String[] args) {
        try {
            if (args.length < 5) {
                System.err.println("Usage: SendString <uri> <exchange> <exchangetype> <routingkey> <message>");
                System.exit(1);
            }

            String uri = args[0];
            String exchange = args[1];
            String exchangeType = args[2];
            String routingKey = args[3];
            String message = args[4];

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();
            Channel ch = conn.createChannel();

            ch.exchangeDeclare(exchange, exchangeType);
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
