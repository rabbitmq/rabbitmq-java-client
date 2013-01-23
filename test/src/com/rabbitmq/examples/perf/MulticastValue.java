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

class MulticastValue implements VariableValue {
    private final String name;
    private final Object value;

    MulticastValue(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public void setup(MulticastParams params) {
        PerfUtil.setValue(params, name, value);
    }

    public void teardown(MulticastParams params) {
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }
}
