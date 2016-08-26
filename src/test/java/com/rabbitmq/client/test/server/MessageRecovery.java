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

package com.rabbitmq.client.test.server;

import java.io.IOException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.ConfirmBase;
import org.junit.Test;

public class MessageRecovery extends ConfirmBase
{

    private final static String Q = "recovery-test";
    private final static String Q2 = "recovery-test-ha-check";

    @Test public void messageRecovery()
        throws Exception
    {
        channel.queueDelete(Q);
        channel.queueDelete(Q2);
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
        // subsequently deleting, the secondary, non-durable queue,
        // which only succeeds if the queue survived the restart,
        // which in turn implies that it must have been a HA queue
        // with slave(s).
        // NB: this wont work when running against a single node broker
        // and running the test individually outside of the HA suite
        boolean expectDelivered = HATests.HA_TESTS_RUNNING;
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
        channel.queueDelete(Q2);
    }

}
