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

import java.util.ArrayList;
import java.util.List;

public class VaryingScenario implements Scenario {
    private final String name;
    private final ConnectionFactory factory;
    private final MulticastParams[] params;
    private final VaryingScenarioStats stats = new VaryingScenarioStats();
    private final Variable[] variables;

    public VaryingScenario(String name, ConnectionFactory factory,
                           MulticastParams params, Variable... variables) {
        this(name, factory, new MulticastParams[]{params}, variables);
    }

    public VaryingScenario(String name, ConnectionFactory factory,
                           MulticastParams[] params, Variable... variables) {
        this.name = name;
        this.factory = factory;
        this.params = params;
        this.variables = variables;
    }

    public void run() throws Exception {
        run(variables, new ArrayList<VariableValue>());
    }

    private void run(Variable[] variables, List<VariableValue> values) throws Exception {
        if (variables.length > 0) {
            Variable variable = variables[0];
            Variable[] rest = rest(variables);
            for (VariableValue value : variable.getValues()) {
                List<VariableValue> values2 = new ArrayList<VariableValue>(values);
                values2.add(value);
                run(rest, values2);
            }
        }
        else {
            SimpleScenarioStats stats0 = stats.next(values);
            for (MulticastParams p : params) {
                for (VariableValue value : values) {
                    value.setup(p);
                }
                MulticastSet set = new MulticastSet(stats0, factory, p);
                stats0.setup(p);
                set.run();
                for (VariableValue value : values) {
                    value.teardown(p);
                }
            }
            System.out.print("#");
            System.out.flush();
        }
    }

    private Variable[] rest(Variable[] variables) {
        Variable[] tail = new Variable[variables.length - 1];
        System.arraycopy(variables, 1, tail, 0, tail.length);
        return tail;
    }

    public ScenarioStats getStats() {
        return stats;
    }

    public String getName() {
        return name;
    }
}
