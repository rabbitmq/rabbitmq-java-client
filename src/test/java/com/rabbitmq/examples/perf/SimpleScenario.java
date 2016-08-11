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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class SimpleScenario implements Scenario {
    private final String name;
    private final ConnectionFactory factory;
    private final MulticastParams[] params;
    private final long interval;
    private SimpleScenarioStats stats;

    public SimpleScenario(String name, ConnectionFactory factory, MulticastParams... params) {
        this(name, factory, 1000L, params);
    }

    public SimpleScenario(String name, ConnectionFactory factory, long interval, MulticastParams... params) {
        this.name = name;
        this.factory = factory;
        this.params = params;
        this.interval = interval;
    }

    public void run() throws IOException, InterruptedException, TimeoutException {
        this.stats = new SimpleScenarioStats(interval);
        for (MulticastParams p : params) {
            MulticastSet set = new MulticastSet(stats, factory, p);
            stats.setup(p);
            set.run();
        }
    }

    public SimpleScenarioStats getStats() {
        return stats;
    }

    public String getName() {
        return name;
    }
}
