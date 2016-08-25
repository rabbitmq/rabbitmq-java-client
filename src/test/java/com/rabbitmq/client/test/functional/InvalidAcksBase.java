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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

import org.junit.Test;

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

    @Test public void doubleAck()
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

    @Test public void crazyAck()
        throws IOException
    {
        select();
        channel.basicAck(123456, false);
        expectError(AMQP.PRECONDITION_FAILED);
    }
}
