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

public class PerformanceMain {
    private static final ConnectionFactory factory = new ConnectionFactory();

    private static final List<?> NO_FLAGS = Arrays.asList();
    private static final List<?> PERSISTENT = Arrays.asList("persistent");

    public static void main(String[] args) throws Exception {
        runStaticBrokerTests();
        runTests(new Scenario[]{varyingBroker()});
    }

    private static void runStaticBrokerTests() throws Exception {
        Broker broker = Broker.DEFAULT;
        broker.start();
        runTests(new Scenario[]{simple(), multiple(), varying(), varying2d(), ratevslatency()});
        broker.stop();
    }

    private static void runTests(Scenario[] scenarios) throws Exception {
        for (Scenario scenario : scenarios) {
            System.out.println();
            System.out.print("Running scenario '" + scenario.getName() + "' ");
            scenario.run();
            System.out.println();
            System.out.println();
            scenario.getStats().print();
        }
    }

    private static Scenario simple() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        return new SimpleScenario("simplest", factory, params);
    }

    private static Scenario multiple() throws IOException, InterruptedException {
        // TODO builder?
        ProducerConsumerParams no_acks = new ProducerConsumerParams();
        ProducerConsumerParams acks = new ProducerConsumerParams();
        acks.setAutoAck(false);
        ProducerConsumerParams acks_confirms = new ProducerConsumerParams();
        acks_confirms.setAutoAck(false);
        acks_confirms.setConfirm(1000);
        ProducerConsumerParams a_c_persistent = new ProducerConsumerParams();
        a_c_persistent.setAutoAck(false);
        a_c_persistent.setConfirm(1000);
        a_c_persistent.setFlags(PERSISTENT);

        return new MultipleScenario("message_options", factory,
            opt("no_acks",        no_acks),
            opt("acks",           acks),
            opt("acks_confirms",  acks_confirms),
            opt("a_c_persistent", a_c_persistent));
    }

    private static Scenario varying() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        return new VaryingScenario("message_sizes", factory, params,
                    var("minMsgSize", 0, 100, 1000, 10000));
    }

    private static Scenario varying2d() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        params.setConsumerCount(0);
        return new VaryingScenario("message_sizes_and_producers", factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    var("producerCount", 1, 2, 5, 10));
    }

    private static Scenario varyingBroker() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        return new VaryingScenario("message_sizes_and_broker_config", factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    new BrokerVariable(Broker.DEFAULT, Broker.HIPE, Broker.COARSE, Broker.HIPE_COARSE));
    }

    private static Scenario ratevslatency() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        return new RateVsLatencyScenario("rate_vs_latency", factory, params);
    }

    private static Variable var(String name, Object... values) {
        return new ProducerConsumerVariable(name, values);
    }

    private static OptionValue opt(String name, ProducerConsumerParams params) {
        return new OptionValue(name, params);
    }
}