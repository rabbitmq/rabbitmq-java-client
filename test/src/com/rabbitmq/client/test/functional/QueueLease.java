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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.test.BrokerTestCase;

public class QueueLease extends BrokerTestCase {

    private final static String TEST_EXPIRE_QUEUE = "leaseq";
    private final static String TEST_NORMAL_QUEUE = "noleaseq";
    private final static String TEST_EXPIRE_REDECLARE_QUEUE = "equivexpire";

    // Currently the expiration timer is very responsive but this may
    // very well change in the future, so tweak accordingly.
    private final static long QUEUE_EXPIRES = 1000L; // msecs
    private final static int SHOULD_EXPIRE_WITHIN = 2000;

    /**
     * Verify that a queue with the 'x-expires` flag is actually deleted within
     * a sensible period of time after expiry.
     */
    public void testQueueExpires() throws IOException, InterruptedException {
        verifyQueueExpires(TEST_EXPIRE_QUEUE, true);
    }

    /**
     * Verify that the server does not delete normal queues... ;)
     */
    public void testDoesNotExpireOthers() throws IOException,
            InterruptedException {
        verifyQueueExpires(TEST_NORMAL_QUEUE, false);
    }

    /**
     * Verify that the server throws an error if the type of x-expires is not
     * long.
     */
    public void testExpireMustBeLong() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 100);

        try {
            channel
                    .queueDeclare("expiresMustBeLong", false, false, false,
                            args);
            fail("server accepted x-expires not of type long");
        } catch (IOException e) {
        }
    }

    public void testExpireMustBeGtZero() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 0L);

        try {
            channel.queueDeclare("expiresMustBeGtZero", false, false, false,
                    args);
            fail("server accepted x-expires of zero ms.");
        } catch (IOException e) {
        }
    }

    public void testExpireMustBePositive() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", -10L);

        try {
            channel.queueDeclare("expiresMustBePositive", false, false, false,
                    args);
            fail("server accepted negative x-expires.");
        } catch (IOException e) {
        }
    }

    /**
     * Verify that the server throws an error if the client redeclares a queue
     * with mismatching 'x-expires' values.
     */
    public void testQueueRedeclareEquivalence() throws IOException {
        Map<String, Object> args1 = new HashMap<String, Object>();
        args1.put("x-expires", 10000L);
        Map<String, Object> args2 = new HashMap<String, Object>();
        args2.put("x-expires", 20000L);

        channel.queueDeclare(TEST_EXPIRE_REDECLARE_QUEUE, false, false, false,
                args1);

        try {
            channel.queueDeclare(TEST_EXPIRE_REDECLARE_QUEUE, false, false,
                    false, args2);
            fail("Able to redeclare queue with mismatching expire flags.");
        } catch (IOException e) {
        }
    }

    void verifyQueueExpires(String name, boolean expire) throws IOException,
            InterruptedException {
        Map<String, Object> args = new HashMap<String, Object>();
        if (expire) {
            args.put("x-expires", QUEUE_EXPIRES);
        }

        channel.queueDeclare(name, false, false, false, args);

        Thread.sleep(SHOULD_EXPIRE_WITHIN / 4);

        try {
            channel.queueDeclarePassive(name);
        } catch (IOException e) {
            fail("Queue expired before deadline.");
        }

        Thread.sleep(SHOULD_EXPIRE_WITHIN); // be on the safe side

        try {
            channel.queueDeclarePassive(name);
            if (expire) {
                fail("Queue should have been expired by now.");
            }
        } catch (IOException e) {
            if (!expire) {
                fail("Queue without expire flag deleted.");
            }
        }
    }

    protected void releaseResources() throws IOException {
        try {
            channel.queueDelete(TEST_NORMAL_QUEUE);
            channel.queueDelete(TEST_EXPIRE_QUEUE);
            channel.queueDelete(TEST_EXPIRE_REDECLARE_QUEUE);
        } catch (IOException e) {
        }

        super.releaseResources();
    }
}
