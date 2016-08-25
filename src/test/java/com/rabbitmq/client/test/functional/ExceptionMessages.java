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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.UUID;

import org.junit.Test;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.test.BrokerTestCase;

public class ExceptionMessages extends BrokerTestCase {
    @Test public void alreadyClosedExceptionMessageWithChannelError() throws IOException {
        String uuid = UUID.randomUUID().toString();
        try {
            channel.queueDeclarePassive(uuid);
            fail("expected queueDeclarePassive to throw");
        } catch (IOException e) {
            // ignored
        }

        try {
            channel.queueDeclarePassive(uuid);
            fail("expected queueDeclarePassive to throw");
        } catch (AlreadyClosedException ace) {
            assertTrue(ace.getMessage().startsWith("channel is already closed due to channel error"));
        }
    }

    @Test public void alreadyClosedExceptionMessageWithCleanClose() throws IOException {
        String uuid = UUID.randomUUID().toString();

        try {
            channel.abort();
            channel.queueDeclare(uuid, false, false, false, null);
        } catch (AlreadyClosedException ace) {
            assertTrue(ace.getMessage().startsWith("channel is already closed due to clean channel shutdown"));
        }
    }
}
