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

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PerformanceMain {
    private static final int interval = 1;
    private static final int confirm = -1;
    private static final int consumerCount = 1;
    private static final int producerCount = 1;
    private static final int consumerTxSize = 0;
    private static final int producerTxSize = 0;
    private static final int prefetchCount = 0;
    private static final int minMsgSize = 0;

    private static final int timeLimit = 10;
    private static final int rateLimit = 0;

    private static final String exchangeName = "test";
    private static final String exchangeType = "fanout";
    private static final String queueName = "";

    private static final boolean autoAck = true;
    private static final boolean exclusive = true;
    private static final boolean autoDelete = false;

    private static final List<?> flags = Arrays.asList(new String[]{});

    public static void main(String[] args) throws IOException, InterruptedException {
        ScenarioStats stats = new ScenarioStats(1000L * interval,
                                producerCount > 0,
                                consumerCount > 0,
                                (flags.contains("mandatory") ||
                                 flags.contains("immediate")),
                                confirm != -1);
        String id = UUID.randomUUID().toString();
        ConnectionFactory factory = new ConnectionFactory();

        ProducerConsumerSet p = new ProducerConsumerSet(id, stats, factory);
        p.setAutoAck(          autoAck);
        p.setAutoDelete(       autoDelete);
        p.setConfirm(          confirm);
        p.setConsumerCount(    consumerCount);
        p.setConsumerTxSize(   consumerTxSize);
        p.setExchangeName(     exchangeName);
        p.setExchangeType(     exchangeType);
        p.setExclusive(        exclusive);
        p.setFlags(            flags);
        p.setMinMsgSize(       minMsgSize);
        p.setPrefetchCount(    prefetchCount);
        p.setProducerCount(    producerCount);
        p.setProducerTxSize(   producerTxSize);
        p.setQueueName(        queueName);
        p.setRateLimit(        rateLimit);
        p.setTimeLimit(        timeLimit);

        p.run();

        stats.print();
    }

    private static class ScenarioStats extends Stats {
        private static final int ignoreFirst = 3;

        private long elapsed;
        private long ignored = ignoreFirst;

        public ScenarioStats(long interval,
                             boolean sendStatsEnabled, boolean recvStatsEnabled,
                             boolean returnStatsEnabled, boolean confirmStatsEnabled) {
            super(interval, sendStatsEnabled, recvStatsEnabled, returnStatsEnabled, confirmStatsEnabled);
        }

        @Override
        protected void report(long now, long elapsed) {
            if (ignored > 0) {
                ignored--;
            }
            else if (ignored == 0) {
                cumulativeLatencyTotal = 0;
                latencyCountTotal      = 0;
                sendCountTotal         = 0;
                recvCountTotal         = 0;
                ignored = -1;
            }
            else {
                this.elapsed += elapsed;
            }

        }

        public void print() {
            System.out.println("Sent: " + 1000.0 * sendCountTotal / elapsed + " msg/s");
            System.out.println("Recv: " + 1000.0 * recvCountTotal / elapsed + " msg/s");
            System.out.println("Avg latency: " + cumulativeLatencyTotal / (1000L * latencyCountTotal) + "us");
        }
    }
}
