// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.ConfirmBase;
import org.junit.jupiter.api.Test;

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

        assertDelivered(Q, 1, false);
        channel.queueDelete(Q);
        channel.queueDelete(Q2);
    }

}
