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
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
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
        return channel.basicGet(ticket, Q, true);
    }

    private void basicPublish()
        throws IOException
    {
        channel.basicPublish(ticket, X, "",
                             MessageProperties.PERSISTENT_TEXT_PLAIN,
                             "persistent message".getBytes());
    }

    public void testBind()
        throws IOException, Exception
    {
      boolean B = false;
        // Transient exchange
        channel.exchangeDeclare(ticket, X, "direct", false);
        // durable queue
        channel.queueDeclare(ticket, Q, true);
        // The following should raise an exception
        try {
            channel.queueBind(ticket, Q, X, "");
        } catch (IOException ee) {
            // Channel and connection have been closed.  We need to
            // delete the queue below and therefore need to reconnect.
            super.connection=null;
            setUp();
            // Say that the expected behaviour is Ok
            B = true; 
        }
        channel.queueDelete(ticket, Q);
        channel.exchangeDelete(ticket, X);

        assertTrue(B);

    }

}
