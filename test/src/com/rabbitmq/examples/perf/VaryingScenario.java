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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VaryingScenario {
    private ConnectionFactory factory;
    private ProducerConsumerParams params;
    private VaryingScenarioStats stats;
    private Variable[] variables;

    public VaryingScenario(ConnectionFactory factory, ProducerConsumerParams params,
                           Variable... variables) {
        this.factory = factory;
        this.params = params;
        this.variables = variables;
    }

    public void run() throws IOException, InterruptedException {
        stats = new VaryingScenarioStats(variables);
        run(variables, new ArrayList<VariableValue>());
    }

    private void run(Variable[] variables, List<VariableValue> values) throws IOException, InterruptedException {
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
            for (VariableValue value : values) {
                value.setup(params);
            }
            new ProducerConsumerSet(stats.next(values), factory, params).run();
            for (VariableValue value : values) {
                value.teardown(params);
            }
        }
    }

    private Variable[] rest(Variable[] variables) {
        return Arrays.copyOfRange(variables, 1, variables.length);
    }

    public VaryingScenarioStats getStats() {
        return stats;
    }
}
