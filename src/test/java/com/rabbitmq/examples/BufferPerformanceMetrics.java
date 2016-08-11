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
import java.net.Socket;
import java.util.Random;

import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultSocketConfigurator;
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

    public static final double NANOSECONDS_PER_SECOND = 1000 * 1000 * 1000;

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
                    {
                        setUri(uri);
                        setSocketConfigurator(new DefaultSocketConfigurator() {
                            @Override
                            public void configure(Socket socket) throws IOException {
                                socket.setTcpNoDelay(!useNagle);
                                socket.setReceiveBufferSize(bufferSize);
                                socket.setSendBufferSize(bufferSize);
                            }
                        });
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
