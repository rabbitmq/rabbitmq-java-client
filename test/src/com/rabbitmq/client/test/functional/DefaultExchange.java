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

public class DefaultExchange extends BrokerTestCase {
    String queueName;

    @Override
    protected void createResources() throws IOException {
        queueName = channel.queueDeclare().getQueue();
    }

    // See bug 22101: publish and declare are the only operations
    // permitted on the default exchange

    public void testDefaultExchangePublish() throws IOException {
        basicPublishVolatile("", queueName); // Implicit binding
        assertDelivered(queueName, 1);
    }

    public void testBindToDefaultExchange() throws IOException {
        try {
            channel.queueBind(queueName, "", "foobar");
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testUnbindFromDefaultExchange() throws IOException {
        try {
            channel.queueUnbind(queueName, "", queueName);
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testDeclareDefaultExchange() throws IOException {
        try {
            channel.exchangeDeclare("", "direct", true);
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }

    public void testDeleteDefaultExchange() throws IOException {
        try {
            channel.exchangeDelete("");
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.ACCESS_REFUSED, ioe);
        }
    }
}
