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

/**
 * See bug 21846:
 * Basic.Ack is now required to signal a channel error immediately upon
 * detecting an invalid deliveryTag, even if the channel is (Tx-)transacted.
 *
 * Specifically, a client MUST not acknowledge the same message more than once.
 */
public abstract class InvalidAcksBase extends BrokerTestCase {
    protected abstract void select() throws IOException;
    protected abstract void commit() throws IOException;

    public void testDoubleAck()
        throws IOException
    {
        select();
        String q = channel.queueDeclare().getQueue();
        basicPublishVolatile(q);
        commit();

        long tag = channel.basicGet(q, false).getEnvelope().getDeliveryTag();
        channel.basicAck(tag, false);
        channel.basicAck(tag, false);

        expectError(AMQP.PRECONDITION_FAILED);
    }

    public void testCrazyAck()
        throws IOException
    {
        select();
        channel.basicAck(123456, false);
        expectError(AMQP.PRECONDITION_FAILED);
    }
}
