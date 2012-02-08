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

public class SimpleScenario implements Scenario {
    private String name;
    private ConnectionFactory factory;
    private MulticastParams[] params;
    private SimpleScenarioStats stats;

    public SimpleScenario(ConnectionFactory factory, MulticastParams... params) {
        this("untitled", factory, params);
    }

    public SimpleScenario(String name, ConnectionFactory factory, MulticastParams... params) {
        this.name = name;
        this.factory = factory;
        this.params = params;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        stats = new SimpleScenarioStats(1000L);
        for (MulticastParams p : params) {
            MulticastSet set = new MulticastSet(stats, factory, p);
            set.run();
            stats.reset();
        }
    }

    @Override
    public SimpleScenarioStats getStats() {
        return stats;
    }

    @Override
    public String getName() {
        return name;
    }
}
