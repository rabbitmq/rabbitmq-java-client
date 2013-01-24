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

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

/* Declare an exchange, bind a queue to it, then try to delete it,
 * setting if-unused to true.  This should throw an exception. */
public class ExchangeDeleteIfUnused extends BrokerTestCase {
    private final static String EXCHANGE_NAME = "xchg1";
    private final static String ROUTING_KEY = "something";

    protected void createResources()
        throws IOException
    {
        super.createResources();
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
    }

    protected void releaseResources()
        throws IOException
    {
        channel.exchangeDelete(EXCHANGE_NAME);
        super.releaseResources();
    }

    /* Attempt to Exchange.Delete(ifUnused = true) a used exchange.
     * Should throw an exception. */
    public void testExchangeDelete() {
        try {
            channel.exchangeDelete(EXCHANGE_NAME, true);
            fail("Exception expected if exchange in use");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }
}
