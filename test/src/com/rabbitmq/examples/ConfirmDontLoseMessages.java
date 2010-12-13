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
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AckListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import java.io.IOException;

public class ConfirmDontLoseMessages {
    final static int MSG_COUNT = 10000;
    final static String QUEUE_NAME = "confirm-test";
    static ConnectionFactory connectionFactory;

    public static void main(String[] args)
        throws IOException, InterruptedException
    {
        connectionFactory = new ConnectionFactory();

        // Publish MSG_COUNT messages and wait for confirms.
        (new Thread(new Consumer())).start();
        // Consume MSG_COUNT messages.
        (new Thread(new Publisher())).start();
    }

    static class Publisher implements Runnable {
        private volatile SortedSet<Long> ackSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());

        public void run() {
            try {
                long startTime = System.currentTimeMillis();

                // Setup
                Connection conn = connectionFactory.newConnection();
                Channel ch = conn.createChannel();
                ch.queueDeclare(QUEUE_NAME, true, false, true, null);
                ch.confirmSelect(true);
                ch.setAckListener(new AckListener() {
                        public void handleAck(long seqNo,
                                              boolean multiple) {
                            if (multiple) {
                                for (long i = ackSet.first(); i <= seqNo; ++i)
                                    ackSet.remove(i);
                            } else {
                                    ackSet.remove(seqNo);
                            }
                        }
                    });

                // Publish
                for (long i = 0; i < MSG_COUNT; ++i) {
                    ackSet.add(i);
                    ch.basicPublish("", QUEUE_NAME,
                                    MessageProperties.PERSISTENT_BASIC,
                                    "nop".getBytes());
                }

                // Wait
                while (ackSet.size() > 0)
                    Thread.sleep(10);

                // Cleanup
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
                ch.queueDeclare(QUEUE_NAME, true, false, true, null);

                // Consume
                QueueingConsumer qc = new QueueingConsumer(ch);
                ch.basicConsume(QUEUE_NAME, true, qc);
                for (int i = 0; i < MSG_COUNT; ++i) {
                    qc.nextDelivery();
                }

                // Consume
                ch.close();
                conn.close();
            } catch (Throwable e) {
                System.out.println("Whoosh!");
                System.out.print(e);
            }
        }
    }
}
