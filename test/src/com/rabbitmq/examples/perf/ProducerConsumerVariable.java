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

import java.util.ArrayList;
import java.util.List;

public class ProducerConsumerVariable implements Variable {
    private final String name;
    private final List<ProducerConsumerVariableValue> values = new ArrayList<ProducerConsumerVariableValue>();

    public ProducerConsumerVariable(String name, Object... values) {
        this.name = name;
        for (Object v : values) {
            this.values.add(new ProducerConsumerVariableValue(name, v));
        }
    }

    public String getName() {
        return name;
    }

    public List<ProducerConsumerVariableValue> getValues() {
        return values;
    }
}
