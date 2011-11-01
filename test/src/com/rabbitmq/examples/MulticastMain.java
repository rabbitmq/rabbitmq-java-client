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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownSignalException;


public class MulticastMain {

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption('?')) {
                usage(options);
                System.exit(0);
            }

            String exchangeType  = strArg(cmd, 't', "direct");
            String exchangeName  = strArg(cmd, 'e', exchangeType);
            String queueName     = strArg(cmd, 'u', "");
            int samplingInterval = intArg(cmd, 'i', 1);
            int rateLimit        = intArg(cmd, 'r', 0);
            int producerCount    = intArg(cmd, 'x', 1);
            int consumerCount    = intArg(cmd, 'y', 1);
            int producerTxSize   = intArg(cmd, 'm', 0);
            int consumerTxSize   = intArg(cmd, 'n', 0);
            long confirm         = intArg(cmd, 'c', -1);
            boolean autoAck      = cmd.hasOption('a');
            int prefetchCount    = intArg(cmd, 'q', 0);
            int minMsgSize       = intArg(cmd, 's', 0);
            int timeLimit        = intArg(cmd, 'z', 0);
            List<?> flags        = lstArg(cmd, 'f');
            int frameMax         = intArg(cmd, 'M', 0);
            int heartbeat        = intArg(cmd, 'b', 0);
            String uri           = strArg(cmd, 'h', "amqp://localhost");

            boolean exclusive  = "".equals(queueName);
            boolean autoDelete = !exclusive;

            //setup
            String id = UUID.randomUUID().toString();
            Stats stats = new Stats(1000L * samplingInterval);
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            factory.setRequestedFrameMax(frameMax);
            factory.setRequestedHeartbeat(heartbeat);

            Thread[] consumerThreads = new Thread[consumerCount];
            Connection[] consumerConnections = new Connection[consumerCount];
            for (int i = 0; i < consumerCount; i++) {
                System.out.println("starting consumer #" + i);
                Connection conn = factory.newConnection();
                consumerConnections[i] = conn;
                Channel channel = conn.createChannel();
                if (consumerTxSize > 0) channel.txSelect();
                channel.exchangeDeclare(exchangeName, exchangeType);
                String qName =
                        channel.queueDeclare(queueName,
                                             flags.contains("persistent"),
                                             exclusive, autoDelete,
                                             null).getQueue();
                if (prefetchCount > 0) channel.basicQos(prefetchCount);
                channel.queueBind(qName, exchangeName, id);
                Thread t =
                    new Thread(new Consumer(channel, id, qName,
                                            consumerTxSize, autoAck,
                                            stats, timeLimit));
                consumerThreads[i] = t;
                t.start();
            }
            Thread[] producerThreads = new Thread[producerCount];
            Connection[] producerConnections = new Connection[producerCount];
            Channel[] producerChannels = new Channel[producerCount];
            for (int i = 0; i < producerCount; i++) {
                System.out.println("starting producer #" + i);
                Connection conn = factory.newConnection();
                producerConnections[i] = conn;
                Channel channel = conn.createChannel();
                producerChannels[i] = channel;
                if (producerTxSize > 0) channel.txSelect();
                if (confirm >= 0) channel.confirmSelect();
                channel.exchangeDeclare(exchangeName, exchangeType);
                final Producer p = new Producer(channel, exchangeName, id,
                                                flags, producerTxSize,
                                                1000L * samplingInterval,
                                                rateLimit, minMsgSize, timeLimit,
                                                confirm);
                channel.addReturnListener(p);
                channel.addConfirmListener(p);
                Thread t = new Thread(p);
                producerThreads[i] = t;
                t.start();
            }

            for (int i = 0; i < producerCount; i++) {
                producerThreads[i].join();
                producerChannels[i].clearReturnListeners();
                producerChannels[i].clearConfirmListeners();
                producerConnections[i].close();
            }

            for (int i = 0; i < consumerCount; i++) {
                consumerThreads[i].join();
                consumerConnections[i].close();
            }

        }
        catch( ParseException exp ) {
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
            usage(options);
        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("<program>", options);
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("?", "help",      false,"show usage"));
        options.addOption(new Option("h", "uri",       true, "AMQP URI"));
        options.addOption(new Option("t", "type",      true, "exchange type"));
        options.addOption(new Option("e", "exchange",  true, "exchange name"));
        options.addOption(new Option("u", "queue",     true, "queue name"));
        options.addOption(new Option("i", "interval",  true, "sampling interval"));
        options.addOption(new Option("r", "rate",      true, "rate limit"));
        options.addOption(new Option("x", "producers", true, "producer count"));
        options.addOption(new Option("y", "consumers", true, "consumer count"));
        options.addOption(new Option("m", "ptxsize",   true, "producer tx size"));
        options.addOption(new Option("n", "ctxsize",   true, "consumer tx size"));
        options.addOption(new Option("c", "confirm",   true, "max unconfirmed publishes"));
        options.addOption(new Option("a", "autoack",   false,"auto ack"));
        options.addOption(new Option("q", "qos",       true, "qos prefetch count"));
        options.addOption(new Option("s", "size",      true, "message size"));
        options.addOption(new Option("z", "time",      true, "time limit"));
        Option flag =     new Option("f", "flag",      true, "message flag");
        flag.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(flag);
        options.addOption(new Option("M", "framemax",  true, "frame max"));
        options.addOption(new Option("b", "heartbeat", true, "heartbeat interval"));
        return options;
    }

    private static String strArg(CommandLine cmd, char opt, String def) {
        return cmd.getOptionValue(opt, def);
    }

    private static int intArg(CommandLine cmd, char opt, int def) {
        return Integer.parseInt(cmd.getOptionValue(opt, Integer.toString(def)));
    }

    private static List<?> lstArg(CommandLine cmd, char opt) {
        String[] vals = cmd.getOptionValues('f');
        if (vals == null) {
            vals = new String[] {};
        }
        return Arrays.asList(vals);
    }

    public static class Producer implements Runnable, ReturnListener,
                                            ConfirmListener
    {
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
        private int     returnCount;

        private long      confirm;
        private Semaphore confirmPool;
        private long      confirmCount;
        private long      nackCount;
        private volatile SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());

        public Producer(Channel channel, String exchangeName, String id,
                        List<?> flags, int txSize,
                        long interval, int rateLimit, int minMsgSize, int timeLimit,
                        long confirm)
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
            this.confirm      = confirm;
            if (confirm > 0) {
                this.confirmPool  = new Semaphore((int)confirm);
            }
        }

        public synchronized void handleReturn(int replyCode,
                                              String replyText,
                                              String exchange,
                                              String routingKey,
                                              AMQP.BasicProperties properties,
                                              byte[] body)
            throws IOException {
            returnCount++;
        }

        public void handleAck(long seqNo, boolean multiple) {
            handleAckNack(seqNo, multiple, false);
        }

        public void handleNack(long seqNo, boolean multiple) {
            handleAckNack(seqNo, multiple, true);
        }

        private void handleAckNack(long seqNo, boolean multiple,
                                   boolean nack) {
            int numConfirms = 0;
            if (multiple) {
                SortedSet<Long> confirmed = unconfirmedSet.headSet(seqNo + 1);
                numConfirms += confirmed.size();
                confirmed.clear();
            } else {
                unconfirmedSet.remove(seqNo);
                numConfirms = 1;
            }
            synchronized (this) {
                if (nack) {
                    nackCount += numConfirms;
                } else {
                    confirmCount += numConfirms;
                }
            }

            if (confirmPool != null) {
                for (int i = 0; i < numConfirms; ++i) {
                    confirmPool.release();
                }
            }

        }

        public void run() {

            long now;
            now = startTime = lastStatsTime = System.currentTimeMillis();
            msgCount = 0;
            int totalMsgCount = 0;

            try {

                while (timeLimit == 0 || now < startTime + timeLimit) {
                    if (confirmPool != null) {
                        confirmPool.acquire();
                    }
                    delay(now);
                    publish(createMessage(totalMsgCount));
                    totalMsgCount++;
                    msgCount++;

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
                               (totalMsgCount * 1000L / (now - startTime)) +
                               " msg/s");

        }

        private void publish(byte[] msg)
            throws IOException {

            unconfirmedSet.add(channel.getNextPublishSeqNo());
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
                long sendRate, returnRate, confirmRate, nackRate;
                synchronized(this) {
                    sendRate     = msgCount     * 1000L / elapsed;
                    returnRate   = returnCount  * 1000L / elapsed;
                    confirmRate  = confirmCount * 1000L / elapsed;
                    nackRate     = nackCount    * 1000L / elapsed;
                    msgCount     = 0;
                    returnCount  = 0;
                    confirmCount = 0;
                    nackCount    = 0;
                }
                System.out.print("sending rate: " + sendRate + " msg/s");
                if (mandatory || immediate) {
                    System.out.print(", returns: " + returnRate + " ret/s");
                }
                if (confirm >= 0) {
                    System.out.print(", confirms: " + confirmRate + " c/s");
                    if (nackRate > 0) {
                        System.out.print(", nacks: " + nackRate + " n/s");
                    }
                }
                System.out.println();
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
        private Channel          channel;
        private String           id;
        private String           queueName;
        private int              txSize;
        private boolean          autoAck;
        private Stats            stats;
        private long             timeLimit;

        public Consumer(Channel channel, String id,
                        String queueName, int txSize, boolean autoAck,
                        Stats stats, int timeLimit) {

            this.channel   = channel;
            this.id        = id;
            this.queueName = queueName;
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

            try {
                q = new QueueingConsumer(channel);
                channel.basicConsume(queueName, autoAck, q);

                while (timeLimit == 0 || now < startTime + timeLimit) {
                    Delivery delivery;
                    try {
                        if (timeLimit == 0) {
                            delivery = q.nextDelivery();
                        } else {
                            delivery = q.nextDelivery(startTime + timeLimit - now);
                            if (delivery == null) break;
                        }
                    } catch (ConsumerCancelledException e) {
                        System.out.println("Consumer cancelled by broker. Re-consuming.");
                        q = new QueueingConsumer(channel);
                        channel.basicConsume(queueName, autoAck, q);
                        continue;
                    }
		    totalMsgCount++;

                    DataInputStream d = new DataInputStream(new ByteArrayInputStream(delivery.getBody()));
                    d.readInt();
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
                                   (totalMsgCount * 1000L / elapsed) +
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
