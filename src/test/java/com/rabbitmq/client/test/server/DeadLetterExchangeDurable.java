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

import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.functional.DeadLetterExchange;
import com.rabbitmq.tools.Host;

public class DeadLetterExchangeDurable extends BrokerTestCase {
    @Override
    protected void createResources() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 5000);
        args.put("x-dead-letter-exchange", DeadLetterExchange.DLX);

        channel.exchangeDeclare(DeadLetterExchange.DLX, "direct", true);
        channel.queueDeclare(DeadLetterExchange.DLQ, true, false, false, null);
        channel.queueDeclare(DeadLetterExchange.TEST_QUEUE_NAME, true, false, false, args);
        channel.queueBind(DeadLetterExchange.TEST_QUEUE_NAME, "amq.direct", "test");
        channel.queueBind(DeadLetterExchange.DLQ, DeadLetterExchange.DLX, "test");
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.exchangeDelete(DeadLetterExchange.DLX);
        channel.queueDelete(DeadLetterExchange.DLQ);
        channel.queueDelete(DeadLetterExchange.TEST_QUEUE_NAME);
    }

    @Test public void deadLetterQueueTTLExpiredWhileDown() throws Exception {
        // This test is nonsensical (and often breaks) in HA mode.
        if (HATests.HA_TESTS_RUNNING) return;

        for(int x = 0; x < DeadLetterExchange.MSG_COUNT; x++) {
            channel.basicPublish("amq.direct", "test", MessageProperties.MINIMAL_PERSISTENT_BASIC, "test message".getBytes());
        }

        closeConnection();
        Host.stopRabbitOnNode();
        Thread.sleep(5000);
        Host.startRabbitOnNode();
        openConnection();
        openChannel();

        //This has the effect of waiting for the queue to complete the
        //dead lettering. Some raciness remains though since the
        //dead-letter publication is async so the 'consume' below may
        //reach the dlq before all dead-lettered messages have arrived
        //there.
        assertNull(basicGet(DeadLetterExchange.TEST_QUEUE_NAME));

        DeadLetterExchange.consume(channel, "expired");
    }
}
