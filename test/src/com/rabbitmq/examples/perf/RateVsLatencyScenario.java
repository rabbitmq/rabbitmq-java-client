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

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.ConnectionFactory;

public class RateVsLatencyScenario implements Scenario {
    private String name;
    private ConnectionFactory factory;
    private MulticastParams params;
    private VaryingScenario impl;

    public RateVsLatencyScenario(String name, ConnectionFactory factory, MulticastParams params) {
        this.name = name;
        this.factory = factory;
        this.params = params;
    }

    public void run() throws Exception {
        SimpleScenario s = new SimpleScenario("untitled", factory, params);
        s.run();
        SimpleScenarioStats m = s.getStats();
        int maxRate = (int) (m.getRecvRate() + m.getSendRate()) / 2;
        Double[] factors = new Double[]{0.01, 0.2, 0.4, 0.6, 0.8, 0.9, 0.95, 0.96, 0.97, 0.98, 0.99, 1.0, 1.01, 1.02, 1.03, 1.04, 1.05};
        Integer [] rates = new Integer[factors.length];
        for (int i = 0; i < rates.length; i++) {
            rates[i] = (int) (factors[i] * maxRate);
        }
        impl = new VaryingScenario("untitled", factory, params,
                new MulticastVariable("rateLimit", (Object[]) rates));
        impl.run();
    }

    public ScenarioStats getStats() {
        return impl.getStats();
    }

    public String getName() {
        return name;
    }
}
