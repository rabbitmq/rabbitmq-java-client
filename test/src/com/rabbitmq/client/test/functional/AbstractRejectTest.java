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
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Arrays;

abstract class AbstractRejectTest extends BrokerTestCase {

    protected Channel secondaryChannel;

    @Override
    protected void setUp()
        throws IOException
    {
        super.setUp();
        secondaryChannel = connection.createChannel();

    }

    @Override
    protected void tearDown()
        throws IOException
    {
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
