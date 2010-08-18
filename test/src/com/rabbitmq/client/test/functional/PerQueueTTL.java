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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 *
 */
public class PerQueueTTL extends BrokerTestCase {

    private static final String TTL_ARG = "x-message-ttl";

    private static final String TTL_QUEUE_NAME = "queue.ttl";

    private static final String TTL_INVALID_QUEUE_NAME = "invalid.queue.ttl";

    public void testCreateQueueWithTTL() throws IOException {
        AMQP.Queue.DeclareOk declareOk = declareQueue(TTL_QUEUE_NAME, 2000);
        assertNotNull(declareOk);
    }

    public void testCreateQueueWithInvalidTTL() throws Exception {
        try {
            declareQueue(TTL_INVALID_QUEUE_NAME, "foobar");
            fail("Should not be able to declare a queue with a non-long value for x-message-ttl");
        } catch (IOException e) {
            assertNotNull(e);
        }
    }

    public void testCreateQueueWithZeroTTL() throws Exception {
        try {
            declareQueue(TTL_INVALID_QUEUE_NAME, 0);
            fail("Should not be able to declare a queue with zero for x-message-ttl");
        } catch (IOException e) {
            assertNotNull(e);
        }
    }

    private AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        AMQP.Queue.DeclareOk declareOk = this.channel.queueDeclare(name, false, false, false,
                Collections.<String, Object>singletonMap(TTL_ARG, ttlValue));
        return declareOk;
    }
}
