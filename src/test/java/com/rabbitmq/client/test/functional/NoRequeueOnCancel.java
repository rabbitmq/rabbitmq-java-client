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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.*;
import org.junit.Test;

import com.rabbitmq.client.test.BrokerTestCase;

public class NoRequeueOnCancel extends BrokerTestCase
{
    protected final String Q = "NoRequeueOnCancel";

    protected void createResources() throws IOException {
      channel.queueDeclare(Q, false, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
    }

    @Test public void noRequeueOnCancel()
        throws IOException, InterruptedException
    {
        channel.basicPublish("", Q, null, "1".getBytes());

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer c = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                latch.countDown();
            }
        };
        String consumerTag = channel.basicConsume(Q, false, c);
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        channel.basicCancel(consumerTag);

        assertNull(channel.basicGet(Q, true));

        closeChannel();
        openChannel();

        assertNotNull(channel.basicGet(Q, true));
    }
}
