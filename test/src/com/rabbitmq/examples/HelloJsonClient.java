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
import com.rabbitmq.tools.jsonrpc.JsonRpcClient;

public class HelloJsonClient {
    public static void main(String[] args) {
        try {
            String request = (args.length > 0) ? args[0] : "Rabbit";
            String hostName = (args.length > 1) ? args[1] : "localhost";
            int portNumber = (args.length > 2) ? Integer.parseInt(args[2]) : AMQP.PROTOCOL.PORT;

            ConnectionFactory cfconn = new ConnectionFactory(); 
            cfconn.setHost(hostName); 
            cfconn.setPort(portNumber);
            Connection conn = cfconn.newConnection();
            Channel ch = conn.createChannel();
            JsonRpcClient client = new JsonRpcClient(ch, "", "Hello");
            HelloJsonService service =
                (HelloJsonService) client.createProxy(HelloJsonService.class);

            System.out.println(service.greeting(request));

            java.util.List<Integer> numbers = new java.util.ArrayList<Integer>();
            numbers.add(1);
            numbers.add(2);
            numbers.add(3);
            System.out.println("1 + 2 + 3 = " + service.sum(numbers));

            conn.close();
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
