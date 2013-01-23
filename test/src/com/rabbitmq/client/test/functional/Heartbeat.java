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

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.test.BrokerTestCase;

public class Heartbeat extends BrokerTestCase {

    public Heartbeat()
    {
        super();
        connectionFactory.setRequestedHeartbeat(1);
    }

    public void testHeartbeat()
        throws IOException, InterruptedException
    {
        assertEquals(1, connection.getHeartbeat());
        Thread.sleep(3100);
        assertTrue(connection.isOpen());
        ((AMQConnection)connection).setHeartbeat(0);
        assertEquals(0, connection.getHeartbeat());
        Thread.sleep(3100);
        assertFalse(connection.isOpen());

    }

}
