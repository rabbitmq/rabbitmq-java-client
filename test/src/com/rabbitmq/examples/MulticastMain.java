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
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionParameters;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.QueueingConsumer.Delivery;


public class MulticastMain {

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            String hostName      = strArg(cmd, 'h', "localhost");
            int portNumber       = intArg(cmd, 'p', AMQP.PROTOCOL.PORT);
            String exchangeType  = strArg(cmd, 't', "direct");
            String exchangeName  = strArg(cmd, 'e', exchangeType);
            int samplingInterval = intArg(cmd, 'i', 1);
            int rateLimit        = intArg(cmd, 'r', 0);
            int producerCount    = intArg(cmd, 'x', 1);
            int consumerCount    = intArg(cmd, 'y', 1);
            int producerTxSize   = intArg(cmd, 'm', 0);
            int consumerTxSize   = intArg(cmd, 'n', 0);
            boolean autoAck      = cmd.hasOption('a');
            int prefetchCount    = intArg(cmd, 'q', 0);
            int minMsgSize       = intArg(cmd, 's', 0);
            int maxRedirects     = intArg(cmd, 'd', 0);
            int timeLimit        = intArg(cmd, 'z', 0);
            List flags           = lstArg(cmd, 'f');

            //setup
            String id = UUID.randomUUID().toString();
            Stats stats = new Stats(1000L * samplingInterval);
            Address[] addresses = new Address[] {
                new Address(hostName, portNumber)
            };
            ConnectionParameters params = new ConnectionParameters();
            Thread[] consumerThreads = new Thread[consumerCount];
            Connection[] consumerConnections = new Connection[consumerCount];
            for (int i = 0; i < consumerCount; i++) {
                System.out.println("starting consumer #" + i);
                Connection conn = new ConnectionFactory(params).newConnection(addresses, maxRedirects);
                consumerConnections[i] = conn;
                Channel channel = conn.createChannel();
                if (consumerTxSize > 0) channel.txSelect();
                channel.exchangeDeclare(exchangeName, exchangeType);
                Queue.DeclareOk res = channel.queueDeclare();
                String queueName = res.getQueue();
                QueueingConsumer consumer = new QueueingConsumer(channel);
                if (prefetchCount > 0) channel.basicQos(prefetchCount);
                channel.basicConsume(queueName, autoAck, consumer);
                channel.queueBind(queueName, exchangeName, id);
                Thread t = 
                    new Thread(new Consumer(consumer, id,
                                            consumerTxSize, autoAck,
                                            stats, timeLimit));
                consumerThreads[i] = t;
                t.start();
            }
            Thread[] producerThreads = new Thread[producerCount];
            Connection[] producerConnections = new Connection[producerCount];
            for (int i = 0; i < producerCount; i++) {
                System.out.println("starting producer #" + i);
                Connection conn = new ConnectionFactory(params).newConnection(addresses, maxRedirects);
                producerConnections[i] = conn;
                Channel channel = conn.createChannel();
                if (producerTxSize > 0) channel.txSelect();
                channel.exchangeDeclare(exchangeName, exchangeType);
                Thread t = 
                    new Thread(new Producer(channel, exchangeName, id,
                                            flags, producerTxSize,
                                            1000L * samplingInterval,
                                            rateLimit, minMsgSize, timeLimit));
                producerThreads[i] = t;
                t.start();
            }

            for (int i = 0; i < producerCount; i++) {
                producerThreads[i].join();
                producerConnections[i].close();
            }

            for (int i = 0; i < consumerCount; i++) {
                consumerThreads[i].join();
                consumerConnections[i].close();
            }

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
        options.addOption(new Option("h", "host",      true, "broker host"));
        options.addOption(new Option("p", "port",      true, "broker port"));
        options.addOption(new Option("t", "type",      true, "exchange type"));
        options.addOption(new Option("e", "exchange",  true, "exchange name"));
        options.addOption(new Option("i", "interval",  true, "sampling interval"));
        options.addOption(new Option("r", "rate",      true, "rate limit"));
        options.addOption(new Option("x", "producers", true, "producer count"));
        options.addOption(new Option("y", "consumers", true, "consumer count"));
        options.addOption(new Option("m", "ptxsize",   true, "producer tx size"));
        options.addOption(new Option("n", "ctxsize",   true, "consumer tx size"));
        options.addOption(new Option("a", "autoack",   false,"auto ack"));
        options.addOption(new Option("q", "qos",       true, "qos prefetch count"));
        options.addOption(new Option("s", "size",      true, "message size"));
        options.addOption(new Option("d", "redirects", true, "max redirects"));
        options.addOption(new Option("z", "time",      true, "time limit"));
        Option flag =     new Option("f", "flag",      true, "message flag");
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
        String[] vals = cmd.getOptionValues('f');
        if (vals == null) {
            vals = new String[] {};
        }
        return Arrays.asList(vals);
    }

    public static class Producer implements Runnable {

        private Channel channel;
        private String  exchangeName;
        private String  id;
        private boolean mandatory;
        private boolean immediate;
        private boolean persistent;
        private int     txSize;
        private long    interval;
        private int     rateLimit;
        private long    timeLimit;

        private byte[]  message;

        private long    startTime;
        private long    lastStatsTime;
        private int     msgCount;

        public Producer(Channel channel, String exchangeName, String id,
                        List flags, int txSize,
                        long interval, int rateLimit, int minMsgSize, int timeLimit)
            throws IOException {

            this.channel      = channel;
            this.exchangeName = exchangeName;
            this.id           = id;
            this.mandatory    = flags.contains("mandatory");
            this.immediate    = flags.contains("immediate");
            this.persistent   = flags.contains("persistent");
            this.txSize       = txSize;
            this.interval     = interval;
            this.rateLimit    = rateLimit;
            this.timeLimit    = 1000L * timeLimit;
            this.message      = new byte[minMsgSize];
        }

        public void run() {

            long now;
            now = startTime = lastStatsTime = System.currentTimeMillis();
            msgCount = 0;
            int totalMsgCount = 0;

            try {

                for (; timeLimit == 0 || now < startTime + timeLimit;
                     totalMsgCount++, msgCount++) {
                    delay(now);
                    publish(createMessage(totalMsgCount));
                    if (txSize != 0 && totalMsgCount % txSize == 0) {
                        channel.txCommit();
                    }
                    now = System.currentTimeMillis();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException (e);
            }

            System.out.println("sending rate avg: " +
                               (totalMsgCount * 1000 / (now - startTime)) +
                               " msg/s");

        }

        private void publish(byte[] msg)
            throws IOException {

            channel.basicPublish(exchangeName, id,
                                 mandatory, immediate,
                                 persistent ? MessageProperties.MINIMAL_PERSISTENT_BASIC : MessageProperties.MINIMAL_BASIC,
                                 msg);
        }

        private void delay(long now)
            throws InterruptedException {

            long elapsed = now - lastStatsTime;
            //example: rateLimit is 5000 msg/s,
            //10 ms have elapsed, we have sent 200 messages
            //the 200 msgs we have actually sent should have taken us
            //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
            long pause = rateLimit == 0 ?
                0 : (msgCount * 1000L / rateLimit - elapsed);
            if (pause > 0) {
                Thread.sleep(pause);
            }
            if (elapsed > interval) {
                System.out.println("sending rate: " +
                                   (msgCount * 1000 / elapsed) +
                                   " msg/s");
                msgCount = 0;
                lastStatsTime = now;
            }
        }

        private byte[] createMessage(int sequenceNumber)
            throws IOException {

            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(acc);
            long nano = System.nanoTime();
            d.writeInt(sequenceNumber);
            d.writeLong(nano);
            d.flush();
            acc.flush();
            byte[] m = acc.toByteArray();
            if (m.length <= message.length) {
                System.arraycopy(m, 0, message, 0, m.length);
                return message;
            } else {
                return m;
            }
        }

    }

    public static class Consumer implements Runnable {

        private QueueingConsumer q;
        private String           id;
        private int              txSize;
        private boolean          autoAck;
        private Stats            stats;
        private long             timeLimit;

        public Consumer(QueueingConsumer q, String id,
                        int txSize, boolean autoAck,
                        Stats stats, int timeLimit) {

            this.q         = q;
            this.id        = id;
            this.txSize    = txSize;
            this.autoAck   = autoAck;
            this.stats     = stats;
            this.timeLimit = 1000L * timeLimit;
        }

        public void run() {

            long now;
            long startTime;
            startTime = now = System.currentTimeMillis();
            int totalMsgCount = 0;

            Channel channel = q.getChannel();

            try {

                for (; timeLimit == 0 || now < startTime + timeLimit;
                     totalMsgCount++) {
                    Delivery delivery;
                    if (timeLimit == 0) {
                        delivery = q.nextDelivery();
                    } else {
                        delivery = q.nextDelivery(startTime + timeLimit - now);
                        if (delivery == null) break;
                    }

                    DataInputStream d = new DataInputStream(new ByteArrayInputStream(delivery.getBody()));
                    int msgSeq = d.readInt();
                    long msgNano = d.readLong();
                    long nano = System.nanoTime();
                    
                    Envelope envelope = delivery.getEnvelope();

                    if (!autoAck) {
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    }

                    if (txSize != 0 && totalMsgCount % txSize == 0) {
                        channel.txCommit();
                    }

                    now = System.currentTimeMillis();

                    stats.collectStats(now, id.equals(envelope.getRoutingKey()) ? (nano - msgNano) : 0L);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException (e);
            } catch (ShutdownSignalException e) {
                throw new RuntimeException(e);
            }

            long elapsed = now - startTime;
            if (elapsed > 0) {
                System.out.println("recving rate avg: " +
                                   (totalMsgCount * 1000 / elapsed) +
                                   " msg/s");
            }
        }

    }

    public static class Stats {

        private long    interval;

        private long    lastStatsTime;
        private int     msgCount;
        private int     latencyCount;
        private long    minLatency;
        private long    maxLatency;
        private long    cumulativeLatency;

        public Stats(long interval) {
            this.interval = interval;
            reset(System.currentTimeMillis());
        }

        private void reset(long t) {
            lastStatsTime     = t;
            msgCount          = 0;
            latencyCount      = 0;
            minLatency        = Long.MAX_VALUE;
            maxLatency        = Long.MIN_VALUE;
            cumulativeLatency = 0L;
        }

        public synchronized void collectStats(long now, long latency) {
            msgCount++;
            
            if (latency > 0) {
                minLatency = Math.min(minLatency, latency);
                maxLatency = Math.max(maxLatency, latency);
                cumulativeLatency += latency;
                latencyCount++;
            }

            long elapsed = now - lastStatsTime;
            if (elapsed > interval) {
                System.out.println("recving rate: " +
                                   (1000L * msgCount / elapsed) +
                                   " msg/s" +
                                   (latencyCount > 0 ?
                                    ", min/avg/max latency: " +
                                    minLatency/1000L + "/" + 
                                    cumulativeLatency / (1000L * latencyCount) + "/" +
                                    maxLatency/1000L + " microseconds" :
                                    ""));
                reset(now);
            }
            
        }
        
    }

}
