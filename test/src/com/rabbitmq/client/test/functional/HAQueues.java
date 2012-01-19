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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Most tests are in rabbitmq-ha-tests but we want to check at least declaration here
 */
public class HAQueues extends BrokerTestCase {
    Map<String,Object> all = new HashMap<String,Object>();

    public HAQueues()
    {
        super();
        all.put("x-ha-policy", "all");
    }

    public void testRedeclare1()
        throws IOException, InterruptedException
    {
        failRedeclare("ha-all", all, null);
    }

    public void testRedeclare2()
        throws IOException, InterruptedException
    {
        failRedeclare("ha-all", null, all);
    }

    private void failRedeclare(String name, Map<String,Object> args1, Map<String,Object> args2) throws IOException {
        channel.queueDeclare(name, true, true, false, args1);
        try {
            channel.queueDeclare(name, true, true, false, args2);
            fail();
        }
        catch (IOException ie) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ie);
        }
    }
}
