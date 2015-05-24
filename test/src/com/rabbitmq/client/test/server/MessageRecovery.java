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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.ConfirmBase;

import java.io.IOException;

public class MessageRecovery extends ConfirmBase
{

    private final static String Q = "recovery-test";
    private final static String Q2 = "recovery-test-ha-check";

    public void testMessageRecovery()
        throws Exception
    {
        channel.confirmSelect();
        channel.queueDeclare(Q, true, false, false, null);
        channel.basicPublish("", Q, false, false,
                             MessageProperties.PERSISTENT_BASIC,
                             "nop".getBytes());
        waitForConfirms();

        channel.queueDeclare(Q2, false, false, false, null);

        restart();

        // When testing in HA mode the message will be collected from
        // a promoted slave and will have its redelivered flag
        // set. But that only happens if there actually *is* a
        // slave. We test that by passively declaring, and
        // subsequently deletign, the secondary, non-durable queue,
        // which only succeeds if the queue survived the restart,
        // which in turn implies that it must have been a HA queue
        // with slave(s).
        boolean expectDelivered = false;
        try {
            channel.queueDeclarePassive(Q2);
            channel.queueDelete(Q2);
            expectDelivered = true;
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            openChannel();
        }
        assertDelivered(Q, 1, expectDelivered);
        channel.queueDelete(Q);
    }

}
