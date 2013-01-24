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

import java.io.IOException;

public class BrokerValue implements VariableValue {
    private final Broker broker;

    public BrokerValue(Broker broker) {
        this.broker = broker;
    }

    public void setup(MulticastParams params) throws IOException {
        broker.start();
    }

    public void teardown(MulticastParams params) {
        broker.stop();
    }

    public String getName() {
        return "broker_type";
    }

    public String getValue() {
        return broker.getName();
    }
}
