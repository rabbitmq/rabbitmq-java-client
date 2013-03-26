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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Map;

public abstract class ExchangeEquivalenceBase extends BrokerTestCase {
    public void verifyEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        channel.exchangeDeclare(name, type, durable, autoDelete, args);
    }

    // Note: this will close the channel
    public void verifyNotEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        try {
            channel.exchangeDeclare(name, type, durable, autoDelete, args);
            fail("Exchange was supposed to be not equivalent");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
            return;
        }
    }
}
