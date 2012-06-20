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

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;


/**
 * Java Application to explore how performance of sending and receiving messages
 * varies with the buffer size and enabling/disabling Nagle's algorithm.
 * <p/>
 * Repeatedly publishes and consumes 100,000 trivial messages with a randomly chosen
 * buffer size, running twice, once with and once without Nagle's Algorithm enabled.
 */
public class BufferPerformanceMetrics {

    private static final int MESSAGE_COUNT  = 100000;
    private static final byte[] MESSAGE     = "".getBytes();
    private static final int REPEATS        = 1000000;
    private static final int PEAK_SIZE      = 20 * 1024;

    private static final double NANOSECONDS_PER_SECOND = 1.0E9;

    /**
     * @param args command-line parameters
     * <p>
     * One optional parameter:
     * </p>
     * <ul>
     * <li><i>AMQP-uri</i> - the AMQP uri to connect to the broker. Default
     * <code>amqp://localhost</code>.
     * (See {@link ConnectionFactory#setUri(String) setUri()}.)
     * </li>
     * </ul>
     * @throws Exception test
     */
    public static void main(String[] args) throws Exception {
        final String uri = args.length > 0 ? args[0] : "amqp://localhost";

        Random rnd = new Random();

        System.out.println(
            "buffer_size  publish_rate+nagle  consume_rate+nagle  publish_rate-nagle  consume_rate-nagle");

        for(int repeat = 0; repeat < REPEATS; repeat++) {
            int bufferSize = 1 + rnd.nextInt(PEAK_SIZE);

            double
                publishRateNagle   = 0,
                publishRateNoNagle = 0,
                consumeRateNagle   = 0,
                consumeRateNoNagle = 0;

            for(boolean useNagle : new boolean[] { false, true }) {
                ConnectionFactory factory = new PerfConnectionFactory(uri, useNagle, bufferSize);

                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();
                String queueName = channel.queueDeclare().getQueue();

                long startPublish = System.nanoTime();
                for(int i = 0; i < MESSAGE_COUNT; i++) {
                    channel.basicPublish("", queueName,
                                         MessageProperties.BASIC, MESSAGE);
                }
                long publishTime = System.nanoTime() - startPublish;

                QueueingConsumer consumer = new QueueingConsumer(channel);

                long startConsume = System.nanoTime();
                channel.basicConsume(queueName, true, consumer); // consumption starts here

                for(int i = 0; i < MESSAGE_COUNT; i++){
                    consumer.nextDelivery();
                }
                long consumeTime = System.nanoTime() - startConsume;

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

            System.out.println(String.format(
                "%11d  %18.2f  %18.2f  %18.2f  %18.2f",
                bufferSize, publishRateNagle, consumeRateNagle, publishRateNoNagle, consumeRateNoNagle
                ));
        }
    }

    private static class PerfConnectionFactory extends ConnectionFactory {
        private final boolean useNagle;
        private final int bufferSize;

        PerfConnectionFactory(String uriString, boolean useNagle, int bufferSize) throws Exception {
            this.useNagle = useNagle;
            this.bufferSize = bufferSize;
            this.setUri(uriString);
        }

        @Override
        public void configureSocket(Socket socket) throws IOException {
            socket.setTcpNoDelay(!useNagle);
            socket.setReceiveBufferSize(bufferSize);
            socket.setSendBufferSize(bufferSize);
        }
    };

}
