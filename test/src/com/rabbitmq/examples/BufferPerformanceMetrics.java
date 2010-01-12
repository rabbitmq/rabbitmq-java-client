package com.rabbitmq.examples;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import java.util.Arrays;
import java.net.Socket;
import java.io.IOException;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Class to explore how performance of sending and receiving messages varies with the buffer size and 
 * enabling/disabling Nagle's algorithm.
 */
public class BufferPerformanceMetrics{

    public static final String QUEUE = "performance-test-queue";
    public static final String EXCHANGE = "performance-test-exchange";
    public static final String ROUTING_KEY = "performance-test-rk";
    public static final int MESSAGE_COUNT = 10000;
    public static final byte[] MESSAGE = "Hello world".getBytes();
    public static double NANOSECONDS_PER_SECOND = 1000 * 1000 * 1000;
    public static final int REPEATS = 1000000;
    public static final int PEAK_SIZE = 20 * 1024;

    public static void main(String[] args) throws Exception{
        String hostName = args.length > 0 ? args[0] : "localhost"; 

        Random rnd = new Random();

        System.out.println("buffer size, publish rate with nagle, get rate with nagle," + 
            " publish rate without nagle, get rate without nagle");
    
      

        for(int repeat = 0; repeat < REPEATS; repeat++){
            final int bufferSize = 1 + rnd.nextInt(PEAK_SIZE);
                 
            double publishRateNagle = 0, publishRateNoNagle = 0, getRateNagle = 0, getRateNoNagle = 0;
    
            for(final boolean useNagle : new boolean[]{ false, true }){
                ConnectionFactory factory = new ConnectionFactory(){
                    @Override public void configureSocket(Socket socket) throws IOException{
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
                for(int i = 0; i < MESSAGE_COUNT; i++){
                    channel.basicPublish(EXCHANGE, QUEUE, MessageProperties.BASIC, MESSAGE);
                }                 
                long publishTime =    System.nanoTime() - start;

                start = System.nanoTime(); 
                for(int i = 0; i < MESSAGE_COUNT; i++){
                    GetResponse response = channel.basicGet(QUEUE, true);
                    assert(Arrays.equals(MESSAGE, response.getBody()));
                } 
                long getTime =    System.nanoTime() - start;

                double publishRate = MESSAGE_COUNT / (publishTime / NANOSECONDS_PER_SECOND);
                double getRate = MESSAGE_COUNT / (getTime / NANOSECONDS_PER_SECOND);
                if(useNagle){
                    publishRateNagle = publishRate;
                    getRateNagle = getRate;
                } else {
                    publishRateNoNagle = publishRate;
                    getRateNoNagle = getRate;
                }

                connection.close();
                // Small sleep to remove noise from hammering the server. 
                Thread.sleep(100);
            }

            System.out.println(bufferSize + ", " + publishRateNagle + ", " + getRateNagle + ", " + publishRateNoNagle + ", " + getRateNoNagle);
        }
    }
}
