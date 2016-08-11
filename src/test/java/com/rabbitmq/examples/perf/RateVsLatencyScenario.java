// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.ConnectionFactory;

public class RateVsLatencyScenario implements Scenario {
    private final String name;
    private final ConnectionFactory factory;
    private final MulticastParams params;
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
                new MulticastVariable("producerRateLimit", (Object[]) rates));
        impl.run();
    }

    public ScenarioStats getStats() {
        return impl.getStats();
    }

    public String getName() {
        return name;
    }
}
