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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

public class UserIDHeader extends BrokerTestCase {
    public void testValidUserId() throws IOException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().userId("guest").build();
        channel.basicPublish("amq.fanout", "", properties, "".getBytes());
    }

    public void testInvalidUserId() {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().userId("not the guest, honest").build();
        try {
            channel.basicPublish("amq.fanout", "", properties, "".getBytes());
            channel.queueDeclare(); // To flush the channel
            fail("Accepted publish with incorrect user ID");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }
}
