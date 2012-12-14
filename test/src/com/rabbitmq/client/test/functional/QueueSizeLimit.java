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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test queue maxdepth limit.
 */
public class QueueSizeLimit extends BrokerTestCase {

    private final int MAXDEPTH = 5;
    private final String q = "queue";

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        channel.confirmSelect();
    }

    public void testQueueSize() throws IOException, InterruptedException {
        declareQueue();
        for (int i=1; i <= MAXDEPTH; i++){
            channel.basicPublish("", q, null, ("msg" + i).getBytes());
            channel.waitForConfirmsOrDie();
            assertEquals(i, declareQueue());
        }
        for (int i=1; i <= MAXDEPTH; i++){
            channel.basicPublish("", q, null, ("msg overflow").getBytes());
            channel.waitForConfirmsOrDie();
            assertEquals(MAXDEPTH, declareQueue());
        }
    }

    public void testQueueAllUnacked() throws IOException, InterruptedException {
        declareQueue();
        for (int i=1; i <= MAXDEPTH; i++){
            channel.basicPublish ("", q, null, ("msg" + i).getBytes());
            channel.waitForConfirmsOrDie();
            channel.basicGet(q, false);
            assertEquals(0, declareQueue());
        }
        channel.basicPublish("", q, null, ("msg overflow").getBytes());
        channel.waitForConfirmsOrDie();
        assertEquals(null, channel.basicGet(q, false));
    }

    public void testQueueSomeUnacked() throws IOException, InterruptedException {
        declareQueue();
        for (int i=1; i <= MAXDEPTH - 1; i++){
            channel.basicPublish ("", q, null, ("msg" + i).getBytes());
            channel.waitForConfirmsOrDie();
            channel.basicGet(q, false);
            assertEquals(0, declareQueue());
        }
        channel.basicPublish("", q, null, ("msg" + MAXDEPTH).getBytes());
        channel.waitForConfirmsOrDie();
        assertEquals(1, declareQueue());

        channel.basicPublish("", q, null, ("msg overflow").getBytes());
        channel.waitForConfirmsOrDie();
        assertEquals("msg overflow", new String(channel.basicGet(q, false).getBody()));
        assertEquals(null, channel.basicGet(q, false));
    }

    private int declareQueue() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-maxdepth", MAXDEPTH);
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(q, false, true, true, args);
        return ok.getMessageCount();
    }
}
