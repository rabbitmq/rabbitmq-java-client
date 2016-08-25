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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

public abstract class ExchangeEquivalenceBase extends BrokerTestCase {
    public void verifyEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        channel.exchangeDeclare(name, type, durable, autoDelete, args);
    }

    // Note: this will close the channel
    public void verifyNotEquivalent(String name,
            String type, boolean durable, boolean autoDelete,
            Map<String, Object> args) throws IOException {
        channel.exchangeDeclarePassive(name);
        try {
            channel.exchangeDeclare(name, type, durable, autoDelete, args);
            fail("Exchange was supposed to be not equivalent");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }
}
