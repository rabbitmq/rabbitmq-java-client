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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;

public class PersisterRestart1 extends PersisterRestartBase
{

    private static final String Q = "Restart";

    private Channel channel2;

    protected void setUp()
        throws IOException
    {
        super.setUp();
        channel2 = connection.createChannel();
    }

    protected void tearDown()
        throws IOException
    {
        if (channel2 != null) {
            channel2.close();
            channel2 = null;
        }
        super.tearDown();
    }

    protected void publishTwo()
        throws IOException
    {
        basicPublishPersistent(Q);
        basicPublishPersistent(Q);
    }

    protected void ackSecond()
        throws IOException
    {
        GetResponse r;
        assertNotNull(r = channel2.basicGet(Q, false));
        assertNotNull(r = channel2.basicGet(Q, false));
        channel2.basicAck(r.getEnvelope().getDeliveryTag(), false);
    }

    protected void exercisePersister()
        throws IOException
    {
        publishTwo();
        channel.txSelect();
        ackSecond();
        publishTwo();
        channel.txCommit();
        ackSecond();
        publishTwo();
        channel.txRollback();
        publishTwo();
    }

    public void testRestart()
        throws IOException, InterruptedException
    {
        declareDurableQueue(Q);
        exercisePersister();
        forceSnapshot();
        closeChannel();
        openChannel();
        exercisePersister();

        restart();

        assertDelivered(Q, 4, true);
        deleteQueue(Q);
    }

}
