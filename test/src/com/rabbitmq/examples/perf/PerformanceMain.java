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

public class PerformanceMain {
    private static final ConnectionFactory factory = new ConnectionFactory();

    public static void main(String[] args) throws IOException, InterruptedException {
        runStaticBrokerTests();
        varyingBroker();
    }

    private static void runStaticBrokerTests() throws IOException, InterruptedException {
        Broker broker = Broker.DEFAULT;
        broker.start();

        simple();
        varying();
        varying2d();
        ratevslatency();

        broker.stop();
    }

    private static void simple() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        SimpleScenario scenario = new SimpleScenario(factory, params);
        scenario.run();
        scenario.getStats().print();
    }

    private static void varying() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        VaryingScenario scenario = new VaryingScenario(factory, params,
                    var("minMsgSize", 0, 100, 1000, 10000));
        scenario.run();
        scenario.getStats().print();
    }

    private static void varying2d() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        params.setConsumerCount(0);
        VaryingScenario scenario = new VaryingScenario(factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    var("producerCount", 1, 2, 5, 10));
        scenario.run();
        scenario.getStats().print();
    }

    private static void varyingBroker() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        VaryingScenario scenario = new VaryingScenario(factory, params,
                    var("minMsgSize", 0, 1000, 10000),
                    new BrokerVariable(Broker.DEFAULT, Broker.HIPE, Broker.COARSE, Broker.HIPE_COARSE));
        scenario.run();
        scenario.getStats().print();
    }

    private static void ratevslatency() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        RateVsLatencyScenario scenario = new RateVsLatencyScenario(factory, params);
        scenario.run();
        scenario.getStats().print();
    }

    private static Variable var(String name, Object... values) {
        return new ProducerConsumerVariable(name, values);
    }
}
