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
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

/* Declare an exchange, bind a queue to it, then try to delete it,
 * setting if-unused to true.  This should throw an exception. */
public class ExchangeDeleteIfUnused extends BrokerTestCase {
    private final static String EXCHANGE_NAME = "xchg1";
    private final static String ROUTING_KEY = "something";

    protected void createResources()
            throws IOException, TimeoutException {
        super.createResources();
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
    }

    protected void releaseResources()
        throws IOException
    {
        channel.exchangeDelete(EXCHANGE_NAME);
        super.releaseResources();
    }

    /* Attempt to Exchange.Delete(ifUnused = true) a used exchange.
     * Should throw an exception. */
    @Test public void exchangeDelete() {
        try {
            channel.exchangeDelete(EXCHANGE_NAME, true);
            fail("Exception expected if exchange in use");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }
}
