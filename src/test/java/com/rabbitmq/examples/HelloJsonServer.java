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
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;

public class HelloJsonServer {
    public static void main(String[] args) {
        try {
            String uri = (args.length > 0) ? args[0] : "amqp://localhost";

            ConnectionFactory connFactory = new ConnectionFactory();
            connFactory.setUri(uri);
            Connection conn = connFactory.newConnection();
            final Channel ch = conn.createChannel();

            ch.queueDeclare("Hello", false, false, false, null);
            JsonRpcServer server =
                new JsonRpcServer(ch, "Hello", HelloJsonService.class,
                                  new HelloJsonService() {
                                      public String greeting(String name) {
                                          return "Hello, "+name+", from JSON-RPC over AMQP!";
                                      }
                                      public int sum(java.util.List<Integer> args) {
                                          int s = 0;
                                          for (int i: args) { s += i; }
                                          return s;
                                      }
                                  });
            server.mainloop();
        } catch (Exception ex) {
            System.err.println("Main thread caught exception: " + ex);
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
