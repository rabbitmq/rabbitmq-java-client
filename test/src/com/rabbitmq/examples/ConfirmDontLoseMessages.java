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
import com.rabbitmq.client.ConfirmListener;
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
                ch.confirmSelect();
                ch.setConfirmListener(new ConfirmListener() {
                        public void handleAck(long seqNo, boolean multiple) {
                            if (multiple) {
                                ackSet.headSet(seqNo+1).clear();
                            } else {
                                ackSet.remove(seqNo);
                            }
                        }

                        public void handleNack(long seqNo, boolean multiple) {
                            int lost = 0;
                            if (multiple) {
                                SortedSet<Long> nackd =
                                    ackSet.headSet(seqNo+1);
                                lost = nackd.size();
                                nackd.clear();
                            } else {
                                lost = 1;
                                ackSet.remove(seqNo);
                            }
                            System.out.printf("Probably lost %d messages.\n",
                                              lost);
                        }
                    });

                // Publish
                for (long i = 0; i < MSG_COUNT; ++i) {
                    ackSet.add(ch.getNextPublishSeqNo());
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
