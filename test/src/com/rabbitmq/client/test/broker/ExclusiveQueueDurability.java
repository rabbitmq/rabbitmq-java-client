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

package com.rabbitmq.client.test.broker;

import java.util.HashMap;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;

import com.rabbitmq.client.test.functional.PersisterRestartBase;

import com.rabbitmq.tools.Host;

import java.io.IOException;

/**
 * This tests whether exclusive, durable queues are deleted when
 * appropriate (following the scenarios given in bug 20578).
 */
public class ExclusiveQueueDurability extends PersisterRestartBase {

  HashMap<String,Object> noArgs = new HashMap();

  void verifyQueueMissing(Channel channel, String queueName) throws IOException {
    try {
      channel.queueDeclare(queueName);
    }
    catch (IOException ioe) {
      // FIXME check that it's specifically resource locked
      fail("Declaring the queue resulted in a channel exception, probably meaning that it already exists");
    }
  }
  
  //  connection and queue are on same node, node restarts
  // -> queue should no longer exist
  public void testConnectionQueueSameNode() throws Exception {
    AMQP.Queue.DeclareOk ok = channel.queueDeclare("scenario1", false, true, true, false, noArgs);
    restartAbruptly();
    verifyQueueMissing(channel, "scenario1");
  }
  
}
