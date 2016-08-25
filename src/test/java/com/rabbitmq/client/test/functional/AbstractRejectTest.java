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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

abstract class AbstractRejectTest extends BrokerTestCase {

    protected Channel secondaryChannel;

    @Override
    public void setUp()
            throws IOException, TimeoutException {
        super.setUp();
        secondaryChannel = connection.createChannel();

    }

    @Override
    public void tearDown()
            throws IOException, TimeoutException {
        if (secondaryChannel != null) {
            secondaryChannel.abort();
            secondaryChannel = null;
        }
        super.tearDown();
    }

    protected long checkDelivery(QueueingConsumer.Delivery d,
                                 byte[] msg, boolean redelivered)
    {
        assertNotNull(d);
        return checkDelivery(d.getEnvelope(), d.getBody(), msg, redelivered);
    }

    protected long checkDelivery(GetResponse r, byte[] msg, boolean redelivered)
    {
        assertNotNull(r);
        return checkDelivery(r.getEnvelope(), r.getBody(), msg, redelivered);
    }

    protected long checkDelivery(Envelope e, byte[] m,
                                 byte[] msg, boolean redelivered)
    {
        assertNotNull(e);
        assertTrue(Arrays.equals(m, msg));
        assertEquals(e.isRedeliver(), redelivered);
        return e.getDeliveryTag();
    }
}
