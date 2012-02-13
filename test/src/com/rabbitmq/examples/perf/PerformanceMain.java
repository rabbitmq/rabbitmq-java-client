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
import com.rabbitmq.tools.json.JSONWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerformanceMain {
    private static final ConnectionFactory factory = new ConnectionFactory();

    //private static final List<?> NO_FLAGS = Arrays.asList();
    private static final List<?> PERSISTENT = Arrays.asList("persistent");
    private static final List<?> MANDATORY = Arrays.asList("mandatory");
    private static final List<?> IMMEDIATE = Arrays.asList("immediate");

    private static Map<String, Object> results = new HashMap<String, Object>();

    public static void main(String[] args) throws Exception {
        runStaticBrokerTests();
        runTests(new Scenario[]{message_size_broker_config()});
        writeJSON();
    }

    private static void writeJSON() throws IOException {
        FileWriter outFile = new FileWriter("results.js");
        PrintWriter out = new PrintWriter(outFile);
        out.println(new JSONWriter(true).write(results));
        outFile.close();
    }

    private static void runStaticBrokerTests() throws Exception {
        Broker broker = Broker.HIPE_COARSE;
        broker.start();
        runTests(new Scenario[]{no_ack_long(), no_consume(), no_ack(), no_ack_mandatory(), no_ack_immediate(), ack(),
                                ack_confirm(), ack_confirm_persist(), ack_persist(), fill_drain_queue("small", 500000),
                                fill_drain_queue("large", 1000000), consumers(),
                                message_sizes(), message_size_vs_producers(), rate_vs_latency()});
        broker.stop();
    }

    private static void runTests(Scenario[] scenarios) throws Exception {
        for (Scenario scenario : scenarios) {
            System.out.print("Running scenario '" + scenario.getName() + "' ");
            scenario.run();
            System.out.println();
            results.put(scenario.getName(), scenario.getStats().results());
        }
    }

    private static Scenario no_ack_long() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setTimeLimit(500);
        return new SimpleScenario("no-ack-long", factory, 10000, params);
    }

    private static Scenario no_consume() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setConsumerCount(0);
        return new SimpleScenario("no-consume", factory, params);
    }

    private static Scenario no_ack() throws IOException, InterruptedException {
        MulticastParams params = params();
        return new SimpleScenario("no-ack", factory, params);
    }

    private static Scenario no_ack_mandatory() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setFlags(MANDATORY);
        return new SimpleScenario("no-ack-mandatory", factory, params);
    }

    private static Scenario no_ack_immediate() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setFlags(IMMEDIATE);
        return new SimpleScenario("no-ack-immediate", factory, params);
    }

    private static Scenario ack() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setAutoAck(false);
        return new SimpleScenario("ack", factory, params);
    }

    private static Scenario ack_confirm() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setAutoAck(false);
        params.setConfirm(10000);
        return new SimpleScenario("ack-confirm", factory, params);
    }

    private static Scenario ack_confirm_persist() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setAutoAck(false);
        params.setConfirm(10000);
        params.setFlags(PERSISTENT);
        return new SimpleScenario("ack-confirm-persist", factory, params);
    }

    private static Scenario ack_persist() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setAutoAck(false);
        params.setFlags(PERSISTENT);
        return new SimpleScenario("ack-persist", factory, params);
    }

    private static Scenario fill_drain_queue(String name, int count) throws IOException, InterruptedException {
        MulticastParams fill = fill_drain_params();
        MulticastParams drain = fill_drain_params();

        fill.setConsumerCount(0);
        fill.setProducerMsgCount(count);
        drain.setProducerCount(0);
        drain.setConsumerMsgCount(count);

        return new SimpleScenario("fill-drain-" + name + "-queue", factory, fill, drain);
    }

    private static MulticastParams fill_drain_params() {
        MulticastParams params = new MulticastParams();
        params.setQueueName("test");
        params.setExclusive(false);
        params.setAutoDelete(true);
        return params;
    }

    private static Scenario message_sizes() throws IOException, InterruptedException {
        MulticastParams params = params();
        return new VaryingScenario("message-sizes", factory, params,
                    var("minMsgSize", 0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                                      1200, 1400, 1600, 1800, 2000, 3000, 4000, 5000));
    }

    private static Scenario consumers() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setAutoAck(false);
        return new VaryingScenario("consumers", factory, params,
                    var("consumerCount", 1, 2, 5, 10, 50, 100, 500),
                    var("prefetchCount", 1, 2, 5, 10, 20, 50, 10000));
    }

    private static Scenario message_size_vs_producers() throws IOException, InterruptedException {
        MulticastParams params = params();
        params.setConsumerCount(0);
        return new VaryingScenario("message-sizes-and-producers", factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    var("producerCount", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }

    private static Scenario message_size_broker_config() throws IOException, InterruptedException {
        MulticastParams params = params();
        return new VaryingScenario("message-sizes-and-broker-config", factory, params,
                    var("minMsgSize", 0, 500, 1000, 1500, 2000),
                    new BrokerVariable(Broker.DEFAULT, Broker.HIPE, Broker.COARSE, Broker.HIPE_COARSE));
    }

    private static Scenario rate_vs_latency() throws IOException, InterruptedException {
        MulticastParams params = params();
        return new RateVsLatencyScenario("rate-vs-latency", factory, params);
    }

    private static MulticastParams params() {
        MulticastParams p = new MulticastParams();
        p.setTimeLimit(30);
        return p;
    }

    private static Variable var(String name, Object... values) {
        return new MulticastVariable(name, values);
    }
}