package com.rabbitmq.examples;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.QueueingConsumer;

import java.net.Socket;
import java.io.IOException;
import java.util.Random;


/**
 * Class to explore how performance of sending and receiving messages
 * varies with the buffer size and enabling/disabling Nagle's
 * algorithm.
 */
public class BufferPerformanceMetrics {

    public static final String QUEUE       = "performance-test-queue";
    public static final String EXCHANGE    = "performance-test-exchange";
    public static final String ROUTING_KEY = "performance-test-rk";
    public static final int MESSAGE_COUNT  = 100000;
    public static final byte[] MESSAGE     = "Hello world".getBytes();
    public static final int REPEATS        = 1000000;
    public static final int PEAK_SIZE      = 20 * 1024;

    public static double NANOSECONDS_PER_SECOND = 1000 * 1000 * 1000;

    public static void main(String[] args) throws Exception {
        String hostName = args.length > 0 ? args[0] : "localhost";

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
                        public void configureSocket(Socket socket)
                            throws IOException {
                            socket.setTcpNoDelay(!useNagle);
                            socket.setReceiveBufferSize(bufferSize);
                            socket.setSendBufferSize(bufferSize);
                        }
                    };

                Connection connection = factory.newConnection(hostName);
                Channel channel = connection.createChannel();
                channel.exchangeDeclare(EXCHANGE, "direct");
                channel.queueDeclare(QUEUE);
                channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);

                long start;

                start = System.nanoTime();

                for(int i = 0; i < MESSAGE_COUNT; i++) {
                    channel.basicPublish(EXCHANGE, ROUTING_KEY,
                                         MessageProperties.BASIC, MESSAGE);
                }

                QueueingConsumer consumer = new QueueingConsumer(channel);
                channel.basicConsume(QUEUE, true, consumer);

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
