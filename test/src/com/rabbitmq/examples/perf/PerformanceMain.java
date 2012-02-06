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
        simple();
        varying();
    }

    private static void simple() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        SimpleScenario scenario = new SimpleScenario(factory, params);
        scenario.run();
        scenario.getStats().print();
    }

    private static void varying() throws IOException, InterruptedException {
        ProducerConsumerParams params = new ProducerConsumerParams();
        VaryingScenario scenario = new VaryingScenario(factory, params, "minMsgSize", new Object[] {0, 100, 10000, 1000000});
        scenario.run();
        scenario.getStats().print();
    }
}
