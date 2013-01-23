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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.examples;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;


/**
 * Class to explore how performance of sending and receiving messages
 * varies with the buffer size and enabling/disabling Nagle's
 * algorithm.
 */
public class BufferPerformanceMetrics {

    public static final int MESSAGE_COUNT  = 100000;
    public static final byte[] MESSAGE     = "".getBytes();
    public static final int REPEATS        = 1000000;
    public static final int PEAK_SIZE      = 20 * 1024;

    public static double NANOSECONDS_PER_SECOND = 1000 * 1000 * 1000;

    public static void main(String[] args) throws Exception {
        final String uri = args.length > 0 ? args[0] : "amqp://localhost";

        Random rnd = new Random();

        System.out.println("buffer size, " +
                           "publish rate with nagle, " +
                           "consume rate with nagle, " +
                           "publish rate without nagle, " +
                           "consume rate without nagle");

        for(int repeat = 0; repeat < REPEATS; repeat++) {
            final int bufferSize = 1 + rnd.nextInt(PEAK_SIZE);

            double
                publishRateNagle   = 0,
                publishRateNoNagle = 0,
                consumeRateNagle   = 0,
                consumeRateNoNagle = 0;

            for(final boolean useNagle : new boolean[] { false, true }) {
                ConnectionFactory factory = new ConnectionFactory() {
                    { setUri(uri); }

                        public void configureSocket(Socket socket)
                            throws IOException {
                            socket.setTcpNoDelay(!useNagle);
                            socket.setReceiveBufferSize(bufferSize);
                            socket.setSendBufferSize(bufferSize);
                        }
                    };

                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();
                Queue.DeclareOk res = channel.queueDeclare();
                String queueName = res.getQueue();

                long start;

                start = System.nanoTime();

                for(int i = 0; i < MESSAGE_COUNT; i++) {
                    channel.basicPublish("", queueName,
                                         MessageProperties.BASIC, MESSAGE);
                }

                QueueingConsumer consumer = new QueueingConsumer(channel);
                channel.basicConsume(queueName, true, consumer);

                long publishTime = System.nanoTime() - start;

                start = System.nanoTime();

                for(int i = 0; i < MESSAGE_COUNT; i++){
                    consumer.nextDelivery();
                }

                long consumeTime = System.nanoTime() - start;

                double publishRate =
                    MESSAGE_COUNT / (publishTime / NANOSECONDS_PER_SECOND);
                double consumeRate =
                    MESSAGE_COUNT / (consumeTime / NANOSECONDS_PER_SECOND);

                if(useNagle){
                    publishRateNagle = publishRate;
                    consumeRateNagle = consumeRate;
                } else {
                    publishRateNoNagle = publishRate;
                    consumeRateNoNagle = consumeRate;
                }

                connection.close();
                // Small sleep to remove noise from hammering the server.
                Thread.sleep(100);
            }

            System.out.println(bufferSize + ", " +
                               publishRateNagle + ", " +
                               consumeRateNagle + ", " +
                               publishRateNoNagle + ", " +
                               consumeRateNoNagle);
        }
    }
}
