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
import com.rabbitmq.tools.jsonrpc.JsonRpcClient;

public class HelloJsonClient {
    private static final int RPC_TIMEOUT_ONE_SECOND = 1000;

    public static void main(String[] args) {
        try {
            String request = (args.length > 0) ? args[0] : "Rabbit";
            String uri = (args.length > 1) ? args[1] : "amqp://localhost";

            ConnectionFactory cfconn = new ConnectionFactory();
            cfconn.setUri(uri);
            Connection conn = cfconn.newConnection();
            Channel ch = conn.createChannel();
            JsonRpcClient client = new JsonRpcClient(ch, "", "Hello", RPC_TIMEOUT_ONE_SECOND);
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
