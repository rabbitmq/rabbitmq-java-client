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
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;

public class DurableOnTransient extends BrokerTestCase
{
    protected static final String Q = "DurableQueue";
    protected static final String X = "TransientExchange";

    private GetResponse basicGet()
        throws IOException
    {
        return channel.basicGet(Q, true);
    }

    private void basicPublish()
        throws IOException
    {
        channel.basicPublish(X, "",
                             MessageProperties.PERSISTENT_TEXT_PLAIN,
                             "persistent message".getBytes());
    }

    protected void createResources() throws IOException {
        // Transient exchange
        channel.exchangeDeclare(X, "direct", false);
        // durable queue
        channel.queueDeclare(Q, true);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
        channel.exchangeDelete(X);
    }

    public void testBind()
        throws IOException
    {
        try {
            channel.queueBind(Q, X, "");
            fail("Expected exception from queueBind");
        } catch (IOException ee) {
            // Pass!
        }
    }
}
