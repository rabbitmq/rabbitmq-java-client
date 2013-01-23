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

import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;

import com.rabbitmq.client.QueueingConsumer;

public class NoRequeueOnCancel extends BrokerTestCase
{
    protected final String Q = "NoRequeueOnCancel";

    protected void createResources() throws IOException {
      channel.queueDeclare(Q, false, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
    }

    public void testNoRequeueOnCancel()
        throws IOException, InterruptedException
    {
        channel.basicPublish("", Q, null, "1".getBytes());

        QueueingConsumer c;

        c = new QueueingConsumer(channel);
        String consumerTag = channel.basicConsume(Q, false, c);
        c.nextDelivery();
        channel.basicCancel(consumerTag);

        assertNull(channel.basicGet(Q, true));

        closeChannel();
        openChannel();

        assertNotNull(channel.basicGet(Q, true));
    }
}
