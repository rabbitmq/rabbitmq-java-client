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

public class DoubleDeletion extends BrokerTestCase
{
    protected static final String Q = "DoubleDeletionQueue";
    protected static final String X = "DoubleDeletionExchange";

    public void testDoubleDeletionQueue()
        throws IOException
    {
      channel.queueDeclare(Q, false, false, false, null);
	channel.queueDelete(Q);
        try {
            channel.queueDelete(Q);
            fail("Expected exception from double deletion of queue");
        } catch (IOException ee) {
	    checkShutdownSignal(AMQP.NOT_FOUND, ee);
            // Pass!
        }
    }

    public void testDoubleDeletionExchange()
        throws IOException
    {
        channel.exchangeDeclare(X, "direct");
	channel.exchangeDelete(X);
        try {
            channel.exchangeDelete(X);
            fail("Expected exception from double deletion of exchange");
        } catch (IOException ee) {
	    checkShutdownSignal(AMQP.NOT_FOUND, ee);
            // Pass!
        }
    }
}
