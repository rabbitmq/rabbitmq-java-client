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

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.ConfirmBase;

public class MessageRecovery extends ConfirmBase
{

    private final static String Q = "recovery-test";

    public void testMessageRecovery()
        throws Exception
    {
        channel.confirmSelect();
        channel.queueDeclare(Q, true, false, false, null);
        channel.basicPublish("", Q, false, false,
                             MessageProperties.PERSISTENT_BASIC,
                             "nop".getBytes());
        waitForConfirms();

        restart();

        // When testing in HA mode the message will be collected from a promoted
        // slave and wil have its redelivered flag set.
        assertDelivered(Q, 1, HATests.HA_TESTS_RUNNING);
        channel.queueDelete(Q);
    }

}
