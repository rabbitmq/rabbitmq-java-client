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

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.tools.Host;

public class RestartBase extends BrokerTestCase
{

    // The time in ms the RabbitMQ persister waits before flushing the
    // persister log
    //
    // This matches the value of LOG_BUNDLE_DELAY in
    // rabbit_persister.erl
    protected static final int PERSISTER_DELAY = 5;

    // The number of entries that the RabbitMQ persister needs to
    // write before it takes a snapshot.
    //
    // This matches the value of MAX_WRAP_ENTRIES in
    // rabbit_persister.erl
    protected final int PERSISTER_SNAPSHOT_THRESHOLD = 500;

    protected void restart()
        throws IOException
    {
        tearDown();
        Host.executeCommand("cd ../rabbitmq-test; make restart-app");
        setUp();
    }

  protected void restartAbruptly()
        throws IOException
    {
        Host.executeCommand("cd ../rabbitmq-test; make restart-node");
        // we do this so that setUp will reconnect
        connection = null;
        setUp();
    }

    protected void forceSnapshot()
        throws IOException, InterruptedException
    {
        Host.executeCommand("cd ../rabbitmq-test; make force-snapshot");
    }

}
