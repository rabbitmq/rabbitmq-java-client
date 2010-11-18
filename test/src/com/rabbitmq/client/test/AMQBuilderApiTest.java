//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.impl.AMQImpl;

import java.io.IOException;

public class AMQBuilderApiTest extends BrokerTestCase
{
    private static final String XCHG_NAME = "builder_test_xchg";

    public void testParticularBuilderForBasicSanityWithRpc() throws IOException
    {
        Method retVal =
                    new AMQImpl.Exchange.Declare.Builder().exchange(XCHG_NAME)
                                                          .type("direct")
                                                          .durable(false)
                                                          .build()
                                                          .rpc(channel);
        assertTrue("Channel should still be open.", channel.isOpen());
        assertTrue(retVal instanceof AMQP.Exchange.DeclareOk);

        retVal = new AMQImpl.Exchange.Delete.Builder().exchange(XCHG_NAME)
                                                      .build()
                                                      .rpc(channel);
        assertTrue("Channel should still be open.", channel.isOpen());
        assertTrue(retVal instanceof AMQP.Exchange.DeleteOk);
    }

    public void testParticularBuilderForBasicSanityWithCall() throws IOException
    {
        new AMQImpl.Exchange.Declare.Builder().exchange(XCHG_NAME)
                                              .type("direct")
                                              .durable(false)
                                              .build()
                                              .call(channel);
        assertTrue("Channel should still be open.", channel.isOpen());

        new AMQImpl.Exchange.Delete.Builder().exchange(XCHG_NAME)
                                             .build()
                                             .call(channel);
        System.out.println("Got to here...");
        assertTrue("Channel should still be open.", channel.isOpen());
    }
}
