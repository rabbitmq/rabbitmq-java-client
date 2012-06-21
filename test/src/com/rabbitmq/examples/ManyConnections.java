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
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;

/**
 * Java Application to create multiple connections with multiple channels,
 * sending and receiving messages on each channel on distinct threads.
 * All messages are sent to and consumed from a common, non-durable, shared, auto-delete queue.
 * <p/>
 * The first thread (thread number 0) outputs statistics for each message it receives.
 */
public class ManyConnections {
    private static final String QUEUE_NAME = "ManyConnections";
    private static double rate;
    private static int connectionCount;
    private static int channelPerConnectionCount;
    private static int heartbeatInterval;

    /**
     * @param args command-line parameters:
     * <p>
     * Four mandatory and one optional positional parameters:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> - the AMQP uri to connect to the broker.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * <li><i>connection-count</i> - the number of connections to create.
     * </li>
     * <li><i>channel-count</i> - the number of channels to create on <i>each</i> connection.
     * </li>
     * <li><i>heartbeat-interval</i> - the heartbeat interval, in seconds, for each channel.
     * Zero means no heartbeats.
     * </li>
     * <li><i>rate</i> - the message rate, in floating point messages per second
     * (<code>0.0 &lt; rate &lt; 50.0</code> is realistic). Default <code>1.0</code>.
     * </li>
     * </ul>
     * <p>
     * There are <i>connection-count</i> x <i>channel-count</i> threads created, each one sending messages at approximately the given rate.
     * </p>
     */
    public static void main(String[] args) {
        try {
            if (args.length < 4) {
                System.err
                .println("Usage: ManyConnections uri connCount chanPerConnCount heartbeatInterval [rate]");
                System.exit(2);
            }

            String uri = args[0];
            connectionCount = Integer.parseInt(args[1]);
            channelPerConnectionCount = Integer.parseInt(args[2]);
            final int totalCount = connectionCount * channelPerConnectionCount;
            heartbeatInterval = Integer.parseInt(args[3]);
            rate = (args.length > 4) ? Double.parseDouble(args[4]) : 1.0;
            final int delayBetweenMessages = (int) (1000 / rate);

            ConnectionFactory factory = new ConnectionFactory();
            factory.setRequestedHeartbeat(heartbeatInterval);
            factory.setUri(uri);

            for (int i = 0; i < connectionCount; i++) {
                System.out.println("Starting connection " + i);
                final Connection conn = factory.newConnection();

                for (int j = 0; j < channelPerConnectionCount; j++) {
                    final Channel ch = conn.createChannel();

                    final int threadNumber = i * channelPerConnectionCount + j;
                    System.out.println("Starting " + threadNumber + " " + ch
                            + " thread...");
                    new Thread
                    ( new ChannelRunnable(threadNumber, ch, delayBetweenMessages, totalCount)
                    , "ManyConnections thread " + threadNumber
                    ).start();
                }
            }
            System.out.println("Started " + totalCount
                    + " channels and threads.");
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private final static class ChannelRunnable implements Runnable {

        private final int threadNumber;
        private final Channel ch;
        private final int delayBetweenMessages;
        private final int totalCount;

        public ChannelRunnable(int threadNumber, Channel ch, int delayBetweenMessages, int totalCount) {
            this.threadNumber = threadNumber;
            this.ch = ch;
            this.delayBetweenMessages = delayBetweenMessages;
            this.totalCount = totalCount;
        }

        public void run() {
            runChannel(threadNumber, ch, delayBetweenMessages, totalCount);
        }

    }

    private final static void runChannel(int threadNumber, final Channel ch, int delayLen, int totalCount) {
        try {
            long startTime = System.currentTimeMillis();

            int msgCount = 0;
            ch.queueDeclare(QUEUE_NAME, false, false, true, null);

            QueueingConsumer consumer = new QueueingConsumer(ch);
            ch.basicConsume(QUEUE_NAME, true, consumer);
            while (true) {
                String toSend = threadNumber + "/" + msgCount++;
                ch.basicPublish("", QUEUE_NAME, null, toSend.getBytes());
                Thread.sleep(delayLen);

                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                if (threadNumber == 0) {
                    long now = System.currentTimeMillis();
                    double delta = (now - startTime) / 1000.0;
                    double actualRate = msgCount / delta;
                    double totalRate = totalCount * actualRate;
                    System.out.println(String.format("thread %3d got message: %20s; "
                            + "%4d messages in %10.3f seconds (%8.2f Hz x %4d channels -> %8.2f Hz)"
                            , threadNumber, "\"" + new String(delivery.getBody()) + "\"", msgCount, delta, actualRate, totalCount, totalRate));
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + threadNumber + " caught exception: "
                    + e);
            e.printStackTrace();
        }
    }
}
