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
import java.util.Arrays;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;


//
// Functional test demonstrating use of an internal exchange in an exchange to
// exchange routing scenario.  The routing topology is:
//
//            -------            -------
//          -/       \-        -/       \-
//         /           \      /           \           +-------------+
//         |    e0     +------|     e1    +-----------+    q1       |
//         \           /      \           /           +-------------+
//          -\       /-        -\       /-
//            -------            -------
//                              (internal)
//
// Where a non-internal exchange is bound to an internal exchange, which in
// turn is bound to a queue.  A client should be able to publish to e0, but
// not to e1, and publications to e0 should be delivered into q1.
//
public class InternalExchange extends BrokerTestCase
{
    private final String[] queues = new String[] { "q1" };
    private final String[] exchanges = new String[] { "e0", "e1" };

    protected void createResources() throws IOException
    {
        // The queues and exchange we create here are all auto-delete, so we
        // don't need to override releaseResources() with their deletions...
        for (String q : queues)
        {
            channel.queueDeclare(q, false, true, true, null);
        }

        // The second exchange, "e1", will be an 'internal' one.
        for ( String e : exchanges )
        {
            channel.exchangeDeclare(e, "direct",
                                    false, true,
                                    !e.equals("e0"),
                                    null);
        }

        channel.exchangeBind("e1", "e0", "");
        channel.queueBind("q1", "e1", "");
    }


    public void testTryPublishingToInternalExchange()
            throws IOException
    {
        byte[] testDataBody = "test-data".getBytes();

        // We should be able to publish to the non-internal exchange as usual
        // and see our message land in the queue...
        channel.basicPublish("e0", "", null, testDataBody);
        GetResponse r = channel.basicGet("q1", true);
        assertTrue(Arrays.equals(r.getBody(), testDataBody));

        // Publishing to the internal exchange will not be allowed...
        channel.basicPublish("e1", "", null, testDataBody);

        expectError(AMQP.ACCESS_REFUSED);
    }
}
