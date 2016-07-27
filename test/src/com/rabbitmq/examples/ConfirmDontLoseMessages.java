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

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

public class ConfirmDontLoseMessages {
    static int msgCount = 10000;
    final static String QUEUE_NAME = "confirm-test";
    static ConnectionFactory connectionFactory;

    public static void main(String[] args)
        throws IOException, InterruptedException
    {
        if (args.length > 0) {
                msgCount = Integer.parseInt(args[0]);
        }

        connectionFactory = new ConnectionFactory();

        // Consume msgCount messages.
        (new Thread(new Consumer())).start();
        // Publish msgCount messages and wait for confirms.
        (new Thread(new Publisher())).start();
    }

    @SuppressWarnings("ThrowablePrintedToSystemOut")
    static class Publisher implements Runnable {
        public void run() {
            try {
                long startTime = System.currentTimeMillis();

                // Setup
                Connection conn = connectionFactory.newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(QUEUE_NAME, true, false, false, null);
                ch.confirmSelect();

                // Publish
                for (long i = 0; i < msgCount; ++i) {
                    ch.basicPublish("", QUEUE_NAME,
                                    MessageProperties.PERSISTENT_BASIC,
                                    "nop".getBytes());
                }

                ch.waitForConfirmsOrDie();

                // Cleanup
                ch.queueDelete(QUEUE_NAME);
                ch.close();
                conn.close();

                long endTime = System.currentTimeMillis();
                System.out.printf("Test took %.3fs\n",
                                  (float)(endTime - startTime)/1000);
            } catch (Throwable e) {
                System.out.println("foobar :(");
                System.out.print(e);
            }
        }
    }

    static class Consumer implements Runnable {
        public void run() {
            try {
                // Setup
                Connection conn = connectionFactory.newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(QUEUE_NAME, true, false, false, null);

                // Consume
                QueueingConsumer qc = new QueueingConsumer(ch);
                ch.basicConsume(QUEUE_NAME, true, qc);
                for (int i = 0; i < msgCount; ++i) {
                    qc.nextDelivery();
                }

                // Cleanup
                ch.close();
                conn.close();
            } catch (Throwable e) {
                System.out.println("Whoosh!");
                System.out.print(e);
            }
        }
    }
}
