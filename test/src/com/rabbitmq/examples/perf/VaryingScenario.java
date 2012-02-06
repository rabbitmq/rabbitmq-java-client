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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
        run(variables, new ArrayList<NameValue>());
    }

    private void run(Variable[] variables, List<NameValue> values) throws IOException, InterruptedException {
        if (variables.length > 0) {
            Variable variable = variables[0];
            Variable[] rest = rest(variables);
            for (Object value : variable.getValues()) {
                List<NameValue> values2 = new ArrayList<NameValue>(values);
                values2.add(new NameValue(variable.getName(), value));
                run(rest, values2);
            }
        }
        else {
            for (NameValue nv : values) {
                setValue(params, nv.getName(), nv.getValue());
            }
            new ProducerConsumerSet(stats.next(values), factory, params).run();
        }
    }

    private Variable[] rest(Variable[] variables) {
        return Arrays.copyOfRange(variables, 1, variables.length);
    }

    private static void setValue(Object obj, String name, Object value) {
        try {
            PropertyDescriptor[] props = Introspector.getBeanInfo(obj.getClass()).getPropertyDescriptors();
            for (PropertyDescriptor prop : props) {
                if (prop.getName().equals(name)) {
                    prop.getWriteMethod().invoke(obj, value);
                    return;
                }
            }
            throw new RuntimeException("Could not find property " + name);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public VaryingScenarioStats getStats() {
        return stats;
    }
}
