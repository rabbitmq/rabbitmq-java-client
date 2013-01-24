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
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;

public class StressPersister {
    public static void main(String[] args) {
        try {
            StressPersister sp = new StressPersister();
            sp.configure(args);
            sp.run();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String strArg(CommandLine cmd, char opt, String def) {
        return cmd.getOptionValue(opt, def);
    }

    private static int intArg(CommandLine cmd, char opt, int def) {
        return Integer.parseInt(cmd.getOptionValue(opt, Integer.toString(def)));
    }

    private static int sizeArg(CommandLine cmd, char opt, int def) {
        String arg = cmd.getOptionValue(opt, Integer.toString(def));
        int multiplier = 1;
        boolean strip = false;
        switch (Character.toLowerCase(arg.charAt(arg.length() - 1))) {
            case 'b': multiplier = 1; strip = true; break;
            case 'k': multiplier = 1024; strip = true; break;
            case 'm': multiplier = 1048576; strip = true; break;
            default: break;
        }
        if (strip) {
            arg = arg.substring(0, arg.length() - 1);
        }
        return multiplier * Integer.parseInt(arg);
    }

    public String uri;

    public String commentText;
    public int backlogSize;
    public int bodySize;
    public int repeatCount;
    public int sampleGranularity;

    public ConnectionFactory connectionFactory;
    public long topStartTime;
    public PrintWriter logOut;

    public void configure(String[] args)
        throws ParseException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        Options options = new Options();
        options.addOption(new Option("h", "uri", true, "AMQP URI"));
        options.addOption(new Option("C", "comment", true, "comment text"));
        options.addOption(new Option("b", "backlog", true, "backlog size"));
        options.addOption(new Option("B", "bodysize", true, "body size"));
        options.addOption(new Option("c", "count", true, "plateau repeat count"));
        options.addOption(new Option("s", "sampleevery", true, "sample granularity"));
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = parser.parse(options, args);

        uri = strArg(cmd, 'h', "amqp://localhost");

        commentText = strArg(cmd, 'C', "");
        if ("".equals(commentText)) {
            throw new IllegalArgumentException("Comment text must be nonempty");
        }

        backlogSize = intArg(cmd, 'b', 5000);
        bodySize = sizeArg(cmd, 'B', 16384);
        repeatCount = intArg(cmd, 'c', backlogSize * 5);
        sampleGranularity = intArg(cmd, 's', Math.max(5, repeatCount / 250));

        connectionFactory = new ConnectionFactory();
        connectionFactory.setUri(uri);
    }

    public Connection newConnection() throws IOException {
        return connectionFactory.newConnection();
    }

    public void run() throws IOException, InterruptedException {
        topStartTime = System.currentTimeMillis();
        String logFileName = String.format("stress-persister-b%08d-B%010d-c%08d-s%06d-%s.out",
                backlogSize, bodySize, repeatCount, sampleGranularity, commentText);
        logOut = new PrintWriter(logFileName);
        System.out.println(logFileName);
        trace("Logging to " + logFileName);
        publishOneInOneOutReceive(backlogSize, bodySize, repeatCount, sampleGranularity);
        logOut.close();
    }

    public void trace(String message) {
        long now = System.currentTimeMillis();
        long delta = now - topStartTime;
        String s = String.format("# %010d ms: %s", delta, message);
        System.out.println(s);
        logOut.println(s);
        logOut.flush();
    }

    public void redeclare(String q, Channel chan) throws IOException {
        trace("Redeclaring queue " + q);
        chan.queueDeclare(q, true, false, false, null);
        // ^^ synchronous operation to get some kind
        // of indication back from the server that it's caught up with us
    }

    public void publishOneInOneOutReceive(int backlogSize, int bodySize, int repeatCount, int sampleGranularity) throws IOException, InterruptedException {
        String q = "test";
        BasicProperties props = MessageProperties.MINIMAL_PERSISTENT_BASIC;
        Connection conn = newConnection();
        Channel chan = conn.createChannel();
        byte[] body = new byte[bodySize];
        List<Long> plateauSampleTimes = new ArrayList<Long>(repeatCount);
        List<Double> plateauSampleDeltas = new ArrayList<Double>(repeatCount);

        trace("Declaring and purging queue " + q);
        chan.queueDeclare(q, true, false, false, null);
        chan.queuePurge(q);
        chan.basicQos(1);

        trace("Building backlog out to " + backlogSize + " messages, each " + bodySize + " bytes long");
        for (int i = 0; i < backlogSize; i++) {
            chan.basicPublish("", q, props, body);
        }

        redeclare(q, chan);

        trace("Beginning plateau of " + repeatCount + " repeats, sampling every " + sampleGranularity + " messages");

        QueueingConsumer consumer = new QueueingConsumer(chan);
        chan.basicConsume(q, consumer);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < repeatCount; i++) {
            if (((i % sampleGranularity) == 0) && (i > 0)) {
                long now = System.currentTimeMillis();
                double delta = 1000 * (now - startTime) / (double) sampleGranularity;
                plateauSampleTimes.add(now);
                plateauSampleDeltas.add(delta);
                System.out.print(String.format("# %3d%%; %012d --> %g microseconds/roundtrip            \r",
                                    (100 * i / repeatCount),
                                    now,
                                    delta));
                startTime = System.currentTimeMillis();
            }
            chan.basicPublish("", q, props, body);
            QueueingConsumer.Delivery d = consumer.nextDelivery();
            chan.basicAck(d.getEnvelope().getDeliveryTag(), false);
        }
        System.out.println();

        trace("Switching QOS to unlimited");
        chan.basicQos(0);

        trace("Draining backlog");
        for (int i = 0; i < backlogSize; i++) {
            QueueingConsumer.Delivery d = consumer.nextDelivery();
            chan.basicAck(d.getEnvelope().getDeliveryTag(), false);
        }

        redeclare(q, chan);

        trace("Closing connection");
        chan.close();
        conn.close();

        trace("Sample results (timestamp in milliseconds since epoch; microseconds/roundtrip)");
        System.out.println("(See log file for results; final sample was " +
                plateauSampleDeltas.get(plateauSampleDeltas.size() - 1) + ")");
        for (int i = 0; i < plateauSampleTimes.size(); i++) {
            String s = String.format("%d %d",
                        plateauSampleTimes.get(i),
                        plateauSampleDeltas.get(i).longValue());
            logOut.println(s);
        }
        logOut.flush();
    }
}
