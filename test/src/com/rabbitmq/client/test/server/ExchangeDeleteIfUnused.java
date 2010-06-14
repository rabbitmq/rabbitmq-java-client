//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.server;

import java.io.IOException;

import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExchangeDeleteIfUnused extends BrokerTestCase {
    private final static String ExchangeName = "xchg1";
    private final static String QueueName = "myQueue";
    private final static String RoutingKey = "something";

    protected void createResources()
        throws IOException
    {
        super.createResources();
        channel.exchangeDeclare(ExchangeName, "direct");
        channel.queueDeclare(QueueName);
        channel.queueBind(QueueName, ExchangeName, RoutingKey);
    }

    protected void releaseResources()
        throws IOException
    {
        channel.queueDelete(QueueName);
        channel.exchangeDelete(ExchangeName);
        super.releaseResources();
    }

    public void testExchangeDelete() {
        try {
            // exchange.delete(ifUnused = true)
            // should throw a channel exception if the exchange is being used
            channel.exchangeDelete(ExchangeName, true);
            fail("Exception expected if exchange in use");
        } catch (IOException e) {
            Throwable t = e.getCause();
            assertTrue("Exception should be a ShutdownSignalException",
                       t instanceof ShutdownSignalException);
            assertTrue("Exception should contain ``in use''", t.getMessage().toLowerCase().contains("in use"));
        }
    }
}
