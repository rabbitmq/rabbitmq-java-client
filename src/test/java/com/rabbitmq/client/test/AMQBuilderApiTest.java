// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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
package com.rabbitmq.client.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Method;

public class AMQBuilderApiTest extends BrokerTestCase
{
    private static final String XCHG_NAME = "builder_test_xchg";

    @Test public void particularBuilderForBasicSanityWithRpc() throws IOException
    {
        Method retVal =
                channel.rpc(new AMQP.Exchange.Declare.Builder()
                           .exchange(XCHG_NAME)
                           .type("direct")
                           .durable(false)
                           .build()
                           ).getMethod();

        assertTrue(channel.isOpen(), "Channel should still be open.");
        assertTrue(retVal instanceof AMQP.Exchange.DeclareOk);

        retVal = channel.rpc(new AMQP.Exchange.Delete.Builder()
                            .exchange(XCHG_NAME)
                            .build()
                            ).getMethod();
        
        assertTrue(channel.isOpen(), "Channel should still be open.");
        assertTrue(retVal instanceof AMQP.Exchange.DeleteOk);
    }

    @Test public void particularBuilderForBasicSanityWithAsyncRpc() throws IOException
    {
        channel.asyncRpc(new AMQP.Exchange.Declare.Builder()
                        .exchange(XCHG_NAME)
                        .type("direct")
                        .durable(false)
                        .build()
                        );

        assertTrue(channel.isOpen(), "Channel should still be open.");

        channel.asyncRpc(new AMQP.Exchange.Delete.Builder()
                        .exchange(XCHG_NAME)
                        .build()
                        );

        assertTrue(channel.isOpen(), "Channel should still be open.");
    }

    @Test public void illFormedBuilder()
    {
        try
        {
            new AMQP.Exchange.Declare.Builder().build();
            fail("Should have thrown IllegalStateException");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalStateException);
        }
    }
}
