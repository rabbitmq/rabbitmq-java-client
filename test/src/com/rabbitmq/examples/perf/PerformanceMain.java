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

    private static Map<String, Object> results = new HashMap<String, Object>();

    public static void main(String[] args) throws Exception {
        runStaticBrokerTests();
        runTests(new Scenario[]{varyingBroker()});
        writeJSON();
    }

    private static void writeJSON() throws IOException {
        FileWriter outFile = new FileWriter("results.js");
        PrintWriter out = new PrintWriter(outFile);
        out.println(new JSONWriter().write(results));
        outFile.close();
    }

    private static void runStaticBrokerTests() throws Exception {
        Broker broker = Broker.DEFAULT;
        broker.start();
        runTests(new Scenario[]{no_ack(), ack(), ack_confirm(), ack_confirm_persist(), varying(), varying2d(), ratevslatency()});
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

    private static Scenario no_ack() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        return new SimpleScenario("no_ack", factory, params);
    }

    private static Scenario ack() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        params.setAutoAck(false);
        return new SimpleScenario("ack", factory, params);
    }

    private static Scenario ack_confirm() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        params.setAutoAck(false);
        params.setConfirm(1000);
        return new SimpleScenario("ack_confirm", factory, params);
    }

    private static Scenario ack_confirm_persist() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        params.setAutoAck(false);
        params.setConfirm(1000);
        params.setFlags(PERSISTENT);
        return new SimpleScenario("ack_confirm_persist", factory, params);
    }

    private static Scenario varying() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        return new VaryingScenario("message_sizes", factory, params,
                    var("minMsgSize", 0, 100, 1000, 10000));
    }

    private static Scenario varying2d() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        params.setConsumerCount(0);
        return new VaryingScenario("message_sizes_and_producers", factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    var("producerCount", 1, 2, 5, 10));
    }

    private static Scenario varyingBroker() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        return new VaryingScenario("message_sizes_and_broker_config", factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    new BrokerVariable(Broker.DEFAULT, Broker.HIPE, Broker.COARSE, Broker.HIPE_COARSE));
    }

    private static Scenario ratevslatency() throws IOException, InterruptedException {
        MulticastParams params = new MulticastParams();
        return new RateVsLatencyScenario("rate_vs_latency", factory, params);
    }

    private static Variable var(String name, Object... values) {
        return new MulticastVariable(name, values);
    }
}