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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//
//
package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Method;

/**
 * Test the Builder Api
 */
public class AMQBuilderApiTest extends BrokerTestCase
{
    private static final String XCHG_NAME = "builder_test_xchg";

    /**
     * @throws Exception test failure
     */
    public void testParticularBuilderForBasicSanityWithRpc() throws Exception
    {
        Method retVal =
                channel.rpc(new AMQP.Exchange.Declare.Builder()
                           .exchange(XCHG_NAME)
                           .type("direct")
                           .durable(false)
                           .build()
                           ).getMethod();

        assertTrue("Channel should still be open.", channel.isOpen());
        assertTrue(retVal instanceof AMQP.Exchange.DeclareOk);

        retVal = channel.rpc(new AMQP.Exchange.Delete.Builder()
                            .exchange(XCHG_NAME)
                            .build()
                            ).getMethod();

        assertTrue("Channel should still be open.", channel.isOpen());
        assertTrue(retVal instanceof AMQP.Exchange.DeleteOk);
    }

    /**
     * @throws Exception test failure
     */
    public void testParticularBuilderForBasicSanityWithAsyncRpc() throws Exception
    {
        channel.asyncRpc(new AMQP.Exchange.Declare.Builder()
                        .exchange(XCHG_NAME)
                        .type("direct")
                        .durable(false)
                        .build()
                        );

        assertTrue("Channel should still be open.", channel.isOpen());

        channel.asyncRpc(new AMQP.Exchange.Delete.Builder()
                        .exchange(XCHG_NAME)
                        .build()
                        );

        assertTrue("Channel should still be open.", channel.isOpen());
    }

    /**
     * Insufficient parametrisation results in an exception
     */
    public void testIllFormedBuilder()
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
