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

import java.util.Arrays;
import java.util.List;

import com.rabbitmq.examples.perf.MulticastParams;
import com.rabbitmq.examples.perf.MulticastSet;
import com.rabbitmq.examples.perf.Stats;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.rabbitmq.client.ConnectionFactory;


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
            int multiAckEvery    = intArg(cmd, 'A', 0);
            int prefetchCount    = intArg(cmd, 'q', 0);
            int minMsgSize       = intArg(cmd, 's', 0);
            int timeLimit        = intArg(cmd, 'z', 0);
            int producerMsgCount = intArg(cmd, 'C', 0);
            int consumerMsgCount = intArg(cmd, 'D', 0);
            List<?> flags        = lstArg(cmd, 'f');
            int frameMax         = intArg(cmd, 'M', 0);
            int heartbeat        = intArg(cmd, 'b', 0);
            String uri           = strArg(cmd, 'h', "amqp://localhost");

            boolean exclusive  = "".equals(queueName);

            //setup
            PrintlnStats stats = new PrintlnStats(1000L * samplingInterval,
                                    producerCount > 0,
                                    consumerCount > 0,
                                    (flags.contains("mandatory") ||
                                     flags.contains("immediate")),
                                    confirm != -1);

            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            factory.setRequestedFrameMax(frameMax);
            factory.setRequestedHeartbeat(heartbeat);


            MulticastParams p = new MulticastParams();
            p.setAutoAck(          autoAck);
            p.setAutoDelete(       !exclusive);
            p.setConfirm(          confirm);
            p.setConsumerCount(    consumerCount);
            p.setConsumerMsgCount( consumerMsgCount);
            p.setConsumerTxSize(   consumerTxSize);
            p.setExchangeName(     exchangeName);
            p.setExchangeType(     exchangeType);
            p.setExclusive(        exclusive);
            p.setFlags(            flags);
            p.setMultiAckEvery(    multiAckEvery);
            p.setMinMsgSize(       minMsgSize);
            p.setPrefetchCount(    prefetchCount);
            p.setProducerCount(    producerCount);
            p.setProducerMsgCount( producerMsgCount);
            p.setProducerTxSize(   producerTxSize);
            p.setQueueName(        queueName);
            p.setRateLimit(        rateLimit);
            p.setTimeLimit(        timeLimit);

            MulticastSet set = new MulticastSet(stats, factory, p);
            set.run(true);

            stats.printFinal();
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
        options.addOption(new Option("?", "help",          false,"show usage"));
        options.addOption(new Option("h", "uri",           true, "AMQP URI"));
        options.addOption(new Option("t", "type",          true, "exchange type"));
        options.addOption(new Option("e", "exchange",      true, "exchange name"));
        options.addOption(new Option("u", "queue",         true, "queue name"));
        options.addOption(new Option("i", "interval",      true, "sampling interval"));
        options.addOption(new Option("r", "rate",          true, "rate limit"));
        options.addOption(new Option("x", "producers",     true, "producer count"));
        options.addOption(new Option("y", "consumers",     true, "consumer count"));
        options.addOption(new Option("m", "ptxsize",       true, "producer tx size"));
        options.addOption(new Option("n", "ctxsize",       true, "consumer tx size"));
        options.addOption(new Option("c", "confirm",       true, "max unconfirmed publishes"));
        options.addOption(new Option("a", "autoack",       false,"auto ack"));
        options.addOption(new Option("A", "multiAckEvery", true, "multi ack every"));
        options.addOption(new Option("q", "qos",           true, "qos prefetch count"));
        options.addOption(new Option("s", "size",          true, "message size"));
        options.addOption(new Option("z", "time",          true, "time limit"));
        options.addOption(new Option("C", "pmessages", true, "producer message count"));
        options.addOption(new Option("D", "cmessages", true, "consumer message count"));
        Option flag =     new Option("f", "flag",          true, "message flag");
        flag.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(flag);
        options.addOption(new Option("M", "framemax",      true, "frame max"));
        options.addOption(new Option("b", "heartbeat",     true, "heartbeat interval"));
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

    private static class PrintlnStats extends Stats {
        private boolean sendStatsEnabled;
        private boolean recvStatsEnabled;
        private boolean returnStatsEnabled;
        private boolean confirmStatsEnabled;

        public PrintlnStats(long interval,
                            boolean sendStatsEnabled, boolean recvStatsEnabled,
                            boolean returnStatsEnabled, boolean confirmStatsEnabled) {
            super(interval);
            this.sendStatsEnabled = sendStatsEnabled;
            this.recvStatsEnabled = recvStatsEnabled;
            this.returnStatsEnabled = returnStatsEnabled;
            this.confirmStatsEnabled = confirmStatsEnabled;
        }

        @Override
        protected void report(long now) {
            System.out.print("time: " + String.format("%.3f", (now - startTime)/1000.0) + "s");

            showRate("sent",      sendCountInterval,    sendStatsEnabled,                        elapsedInterval);
            showRate("returned",  returnCountInterval,  sendStatsEnabled && returnStatsEnabled,  elapsedInterval);
            showRate("confirmed", confirmCountInterval, sendStatsEnabled && confirmStatsEnabled, elapsedInterval);
            showRate("nacked",    nackCountInterval,    sendStatsEnabled && confirmStatsEnabled, elapsedInterval);
            showRate("received",  recvCountInterval,    recvStatsEnabled,                        elapsedInterval);

            System.out.print((latencyCountInterval > 0 ?
                              ", min/avg/max latency: " +
                              minLatency/1000L + "/" +
                              cumulativeLatencyInterval / (1000L * latencyCountInterval) + "/" +
                              maxLatency/1000L + " microseconds" :
                              ""));

            System.out.println();
        }

        private void showRate(String descr, long count, boolean display,
                              long elapsed) {
            if (display) {
                System.out.print(", " + descr + ": " + formatRate(1000.0 * count / elapsed) + " msg/s");
            }
        }

        public void printFinal() {
            long now = System.currentTimeMillis();

            System.out.println("sending rate avg: " +
                               formatRate(sendCountTotal * 1000.0 / (now - startTime)) +
                               " msg/s");

            long elapsed = now - startTime;
            if (elapsed > 0) {
                System.out.println("recving rate avg: " +
                                   formatRate(recvCountTotal * 1000.0 / elapsed) +
                                   " msg/s");
            }
        }

        private static String formatRate(double rate) {
            if (rate == 0.0)    return String.format("%d", (long)rate);
            else if (rate < 1)  return String.format("%1.2f", rate);
            else if (rate < 10) return String.format("%1.1f", rate);
            else                return String.format("%d", (long)rate);
        }
    }
}
