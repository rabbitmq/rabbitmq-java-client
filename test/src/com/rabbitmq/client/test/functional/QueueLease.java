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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

public class QueueLease extends BrokerTestCase {

    private final static String TEST_EXPIRE_QUEUE = "leaseq";
    private final static String TEST_NORMAL_QUEUE = "noleaseq";
    private final static String TEST_EXPIRE_REDECLARE_QUEUE = "equivexpire";

    // Currently the expiration timer is very responsive but this may
    // very well change in the future, so tweak accordingly.
    private final static int QUEUE_EXPIRES = 1000; // msecs
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

    public void testExpireMayBeByte() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", (byte)100);

        try {
            channel.queueDeclare("expiresMayBeByte", false, true, false, args);
        } catch (IOException e) {
            fail("server did not accept x-expires of type byte");
        }
    }

    public void testExpireMayBeShort() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", (short)100);

        try {
            channel.queueDeclare("expiresMayBeShort", false, true, false, args);
        } catch (IOException e) {
            fail("server did not accept x-expires of type short");
        }
    }

    public void testExpireMayBeLong() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 100L);

        try {
            channel.queueDeclare("expiresMayBeLong", false, true, false, args);
        } catch (IOException e) {
            fail("server did not accept x-expires of type long");
        }
    }

    public void testExpireMustBeGtZero() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", 0);

        try {
            channel.queueDeclare("expiresMustBeGtZero", false, false, false,
                    args);
            fail("server accepted x-expires of zero ms.");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testExpireMustBePositive() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", -10);

        try {
            channel.queueDeclare("expiresMustBePositive", false, false, false,
                    args);
            fail("server accepted negative x-expires.");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    /**
     * Verify that the server throws an error if the client redeclares a queue
     * with mismatching 'x-expires' values.
     */
    public void testQueueRedeclareEquivalence() throws IOException {
        Map<String, Object> args1 = new HashMap<String, Object>();
        args1.put("x-expires", 10000);
        Map<String, Object> args2 = new HashMap<String, Object>();
        args2.put("x-expires", 20000);

        channel.queueDeclare(TEST_EXPIRE_REDECLARE_QUEUE, false, false, false,
                args1);

        try {
            channel.queueDeclare(TEST_EXPIRE_REDECLARE_QUEUE, false, false,
                    false, args2);
            fail("Able to redeclare queue with mismatching expire flags.");
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    public void testActiveQueueDeclareExtendsLease()
            throws InterruptedException, IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", QUEUE_EXPIRES);
        channel.queueDeclare(TEST_EXPIRE_QUEUE, false, false, false, args);

        Thread.sleep(QUEUE_EXPIRES * 3 / 4);
        try {
            channel.queueDeclare(TEST_EXPIRE_QUEUE, false, false, false, args);
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            fail("Queue expired before active re-declaration.");
        }

        Thread.sleep(QUEUE_EXPIRES * 3 / 4);
        try {
            channel.queueDeclarePassive(TEST_EXPIRE_QUEUE);
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            fail("Queue expired: active re-declaration did not extend lease.");
        }
    }

    public void testPassiveQueueDeclareExtendsLease()
            throws InterruptedException, IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-expires", QUEUE_EXPIRES);
        channel.queueDeclare(TEST_EXPIRE_QUEUE, false, false, false, args);

        Thread.sleep(QUEUE_EXPIRES * 3 / 4);
        try {
            channel.queueDeclarePassive(TEST_EXPIRE_QUEUE);
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            fail("Queue expired before before passive re-declaration.");
        }

        Thread.sleep(QUEUE_EXPIRES * 3 / 4);
        try {
            channel.queueDeclarePassive(TEST_EXPIRE_QUEUE);
        } catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            fail("Queue expired: passive redeclaration did not extend lease.");
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
            checkShutdownSignal(AMQP.NOT_FOUND, e);
            fail("Queue expired before deadline.");
        }

        Thread.sleep(SHOULD_EXPIRE_WITHIN); // be on the safe side

        try {
            channel.queueDeclarePassive(name);
            if (expire) {
                fail("Queue should have been expired by now.");
            }
        } catch (IOException e) {
            if (expire) {
                checkShutdownSignal(AMQP.NOT_FOUND, e);
            } else {
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
