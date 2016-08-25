// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.


package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

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


    @Test public void tryPublishingToInternalExchange()
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
