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
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.ShutdownSignalException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Roundtrip {

    private static int SEARCH_BASE = 10;
    private static int NUMBER_OF_SAMPLES = 1000;
    private static int SAMPLE_DURATION = 1;

    private ConnectionWrapper pConn;
    private ConnectionWrapper cConn;
    private boolean autoAck;
    private boolean mandatory;
    private boolean immediate;
    private boolean persistent;

    private byte[]  message;

    public static void main(String[] args) {

        Options options = getOptions();
        CommandLineParser parser = new GnuParser();

        try {

            CommandLine cmd = parser.parse(options, args);

            SEARCH_BASE          = intArg(cmd, 'b', SEARCH_BASE);
            NUMBER_OF_SAMPLES    = intArg(cmd, 'n', NUMBER_OF_SAMPLES);
            SAMPLE_DURATION      = intArg(cmd, 'd', SAMPLE_DURATION);
            int maxExponent      = intArg(cmd, 'e', 5);
            String hostName      = strArg(cmd, 'h', "localhost");
            int portNumber       = intArg(cmd, 'p', AMQP.PROTOCOL.PORT);
            boolean autoAck      = cmd.hasOption('a');
            int minMsgSize       = intArg(cmd, 's', 0);
            List flags           = lstArg(cmd, 'f');

            ConnectionFactory connectionFactory = new ConnectionFactory();
            ConnectionWrapper pConn = new ConnectionWrapper(connectionFactory);
            ConnectionWrapper cConn = new ConnectionWrapper(connectionFactory);
            pConn.connect(hostName, portNumber);
            cConn.connect(hostName, portNumber);
            Roundtrip rt = new Roundtrip(pConn, cConn,
                                         autoAck, minMsgSize, flags);
            int optimalLag = tune(rt, maxExponent);
            Results r = rt.run(optimalLag);

            cConn.disconnect();
            pConn.disconnect();

            r.display();
        }
        catch( ParseException exp ) {
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("<program>", options);
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("b", "base",    true, "search base"));
        options.addOption(new Option("n", "samples", true, "number of samples"));
        options.addOption(new Option("d", "duration",true, "sample duration"));
        options.addOption(new Option("e", "exponent",true, "max exponent"));
        options.addOption(new Option("h", "host",    true, "broker host"));
        options.addOption(new Option("p", "port",    true, "broker port"));
        options.addOption(new Option("a", "autoack", false,"auto ack"));
        options.addOption(new Option("s", "size",    true, "message size"));
        Option flag =     new Option("f", "flag",    true, "message flag");
        flag.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(flag);
        return options;
    }

    private static String strArg(CommandLine cmd, char opt, String def) {
        return cmd.getOptionValue(opt, def);
    }

    private static int intArg(CommandLine cmd, char opt, int def) {
        return Integer.parseInt(cmd.getOptionValue(opt, Integer.toString(def)));
    }

    private static List lstArg(CommandLine cmd, char opt) {
        String[] vals = cmd.getOptionValues(opt);
        if (vals == null) {
            vals = new String[] {};
        }
        return Arrays.asList(vals);
    }

    private static int tune(Roundtrip rt, int maxE)
        throws IOException {

        // first find a lower and upper bound for an optimal lag value
        // by exponentially increasing the lag until the throughput
        // drops off.
        long maxThroughput = 0;
        long currentThroughput = 0;
        int lagAtMax = 0;
        for (int lagE = 0; lagE <= maxE; lagE++) {
            maxThroughput = Math.max(maxThroughput, currentThroughput);
            int lag = (int)Math.pow(SEARCH_BASE, lagE);
            Results r = rt.run(lag);
            currentThroughput = r.median;
            /*
            System.out.print("" + SEARCH_BASE + "^" + lagE + " =");
            r.displaySamples();
            System.out.println();
            */
            if (currentThroughput >= maxThroughput) {
                lagAtMax = lagE;
            }
        }

        //TODO: implement bisection search

        return (int)Math.pow(SEARCH_BASE, lagAtMax);
    }
      
    public Roundtrip(ConnectionWrapper pConn, ConnectionWrapper cConn,
                     boolean autoAck, int minMsgSize, List flags) {
        this.pConn      = pConn;
        this.cConn      = cConn;
        this.autoAck    = autoAck;
        this.mandatory  = flags.contains("mandatory");
        this.immediate  = flags.contains("immediate");
        this.persistent = flags.contains("persistent");
        this.message    = new byte[minMsgSize];
    }

    public Results run(int lag)
        throws IOException {

        String queueName =
            cConn.channel.queueDeclare(cConn.ticket).getQueue();

        //flood
        for (int i = 0; i < lag; i++) {
            publish(queueName);
        }

        QueueingConsumer consumer = new QueueingConsumer(cConn.channel);
        String consumerTag =
            cConn.channel.basicConsume(cConn.ticket, queueName, autoAck,
                                       consumer);

        //stabilise
        for (int i = 0; i < 2 * lag; i++) {
            consume(consumer);
            publish(queueName);
        }

        //measure
        Results r = measure(consumer, queueName);

        //drain
        for (int i = 0; i < lag; i++) {
            consume(consumer);
        }

        cConn.channel.basicCancel(consumerTag); // will delete the queue

        return r;
    }

    public Results measure(QueueingConsumer consumer, String queueName)
        throws IOException {

        long[] samples = new long[NUMBER_OF_SAMPLES];
        long start;
        long finish;
        long begin;
        begin = finish = System.nanoTime();
        for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
            start = finish;
            int j;
            long diff = 0L;
            for (j = 0; diff < SAMPLE_DURATION * 1000000L; j++) {
                consume(consumer);
                publish(queueName);
                finish = System.nanoTime();
                diff = finish - start;
            }
            samples[i] = 1000000000L * j / diff;
        }
        return new Results(samples);
    }

    private void publish(String queueName)
        throws IOException {
        
        pConn.channel.basicPublish(pConn.ticket, "", queueName,
                                   mandatory, immediate,
                                   persistent ? MessageProperties.MINIMAL_PERSISTENT_BASIC : MessageProperties.MINIMAL_BASIC,
                                   message);
    }

    private void consume(QueueingConsumer consumer)
        throws IOException {

        try {
            Delivery delivery = consumer.nextDelivery();
            if (!autoAck) {
                consumer.getChannel().basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ShutdownSignalException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ConnectionWrapper {

        private ConnectionFactory factory;
        private Connection connection;
        private Channel channel;
        private int ticket;

        public ConnectionWrapper(ConnectionFactory factory) {
            this.factory = factory;
        }

        public void connect(String hostName, int portNumber)
            throws IOException {

            connection = factory.newConnection(hostName, portNumber);
            channel = connection.createChannel();
            ticket = channel.accessRequest("/data");
        }

        public void disconnect()
            throws IOException {

            connection.close(200, "ok");
        }

    }

    public static class Results {

        public long[] samples;
        long median;
        long min;
        long max;
        long minQ;
        long maxQ;
        long mean;
        long sd;

        public Results(long[] samples) {

            this.samples = samples;

            int ss  = samples.length;
            int ss1 = ss - 1;

            long[] ssamples = new long[ss];
            System.arraycopy(samples, 0, ssamples, 0, ss);
            Arrays.sort(ssamples);

            median = ssamples[ss1 / 2];
            min    = ssamples[0];
            max    = ssamples[ss1];
            minQ   = ssamples[ss1 / 4];
            maxQ   = ssamples[ss1 * 3 / 4];

            long sum = 0;
            long ssum = 0;
            for (int i = 0; i < ss; i++) {
                sum += ssamples[i];
                ssum += ssamples[i] * samples[i];
            }

            mean = sum / ss;
            sd = (long)Math.sqrt((ssum - ss * mean * mean) / ss);
        }

        public void display() {
            System.out.println("min = " + min + ", " +
                               "max = " + max + ", " +
                               "minQ = " + minQ + ", " +
                               "maxQ = " + maxQ);
            System.out.println("median = " + median + ", " +
                               "mean = " + mean + ", " +
                               "sd = " + sd);
        }

        public void displaySamples() {
            int ss = samples.length;
            for (int i = 0; i < ss; i++) {
                System.out.print(" " + samples[i]);
            }
        }

    }

}
