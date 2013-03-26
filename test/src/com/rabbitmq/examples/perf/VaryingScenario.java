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

import java.util.ArrayList;
import java.util.List;

public class VaryingScenario implements Scenario {
    private String name;
    private ConnectionFactory factory;
    private MulticastParams[] params;
    private VaryingScenarioStats stats = new VaryingScenarioStats();
    private Variable[] variables;

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
