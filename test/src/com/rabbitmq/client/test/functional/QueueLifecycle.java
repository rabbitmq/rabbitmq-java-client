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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * Test queue auto-delete and exclusive semantics.
 */
public class QueueLifecycle extends BrokerTestCase {

    void verifyQueueExists(String name) throws IOException {
        channel.queueDeclarePassive(name);
    }

    void verifyQueueMissing(String name) throws IOException {
        // we can't in general check with a passive declare, since that
        // may return an IOException because of exclusivity. But we can
        // check that we can happily declare another with the same name:
        // the only circumstance in which this won't result in an error is
        // if it doesn't exist.
        try {
            channel.queueDeclare(name, false, false, false, null);
        } catch (IOException ioe) {
            fail("Queue.Declare threw an exception, probably meaning that the queue already exists");
        }
        // clean up
        channel.queueDelete(name);
    }

    /**
     * Verify that a queue both exists and has the properties as given
     *
     * @throws IOException
     *             if one of these conditions is not true
     */
    void verifyQueue(String name, boolean durable, boolean exclusive,
            boolean autoDelete, Map<String, Object> args) throws IOException {
        verifyQueueExists(name);
        // use passive/equivalent rule to check that it has the same properties
        channel.queueDeclare(name, durable, exclusive, autoDelete, args);
    }

    // NB the exception will close the connection
    void verifyNotEquivalent(boolean durable, boolean exclusive,
            boolean autoDelete) throws IOException {
        String q = "queue";
        channel.queueDeclare(q, false, false, false, null);
        try {
            verifyQueue(q, durable, exclusive, autoDelete, null);
        } catch (IOException ioe) {
            if (exclusive)
                checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            else
                checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
            return;
        }
        fail("Queue.declare should have been rejected as not equivalent");
    }

    /** From amqp-0-9-1.xml, for "passive" property, "equivalent" rule:
     * "If not set and the queue exists, the server MUST check that the
     * existing queue has the same values for durable, exclusive,
     * auto-delete, and arguments fields. The server MUST respond with
     * Declare-Ok if the requested queue matches these fields, and MUST
     * raise a channel exception if not."
     */
    public void testQueueEquivalence() throws IOException {
        String q = "queue";
        channel.queueDeclare(q, false, false, false, null);
        // equivalent
        verifyQueue(q, false, false, false, null);

        // the spec says that the arguments table is matched on
        // being semantically equivalent.
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put("assumed-to-be-semantically-void", "bar");
        verifyQueue(q, false, false, false, args);

    }

    // not equivalent in various ways
    public void testQueueNonEquivalenceDurable() throws IOException {
        verifyNotEquivalent(true, false, false);
    }

    public void testQueueNonEquivalenceExclusive() throws IOException {
        verifyNotEquivalent(false, true, false);
    }

    public void testQueueNonEquivalenceAutoDelete() throws IOException {
        verifyNotEquivalent(false, false, true);
    }

    // Note that this assumes that auto-deletion is synchronous with
    // basic.cancel,
    // which is not actually in the spec. (If it isn't, there's a race here).
    public void testQueueAutoDelete() throws IOException {
        String name = "tempqueue";
        channel.queueDeclare(name, false, false, true, null);
        // now it's there
        verifyQueue(name, false, false, true, null);
        QueueingConsumer consumer = new QueueingConsumer(channel);
        String consumerTag = channel.basicConsume(name, consumer);
        channel.basicCancel(consumerTag);
        // now it's not .. we hope
        try {
            verifyQueueExists(name);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.NOT_FOUND, ioe);
            return;
        }
        fail("Queue should have been auto-deleted after we removed its only consumer");
    }

    public void testExclusiveNotAutoDelete() throws IOException {
        String name = "exclusivequeue";
        channel.queueDeclare(name, false, true, false, null);
        // now it's there
        verifyQueue(name, false, true, false, null);
        QueueingConsumer consumer = new QueueingConsumer(channel);
        String consumerTag = channel.basicConsume(name, consumer);
        channel.basicCancel(consumerTag);
        // and still there, because exclusive no longer implies autodelete
        verifyQueueExists(name);
    }

    public void testExclusiveGoesWithConnection() throws IOException {
        String name = "exclusivequeue2";
        channel.queueDeclare(name, false, true, false, null);
        // now it's there
        verifyQueue(name, false, true, false, null);
        closeConnection();
        openConnection();
        openChannel();
        verifyQueueMissing(name);
    }

    public void testArgumentArrays() throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        String[] arr = new String[]{"foo", "bar", "baz"};
        args.put("my-key", arr);
        String queueName = "argumentArraysQueue";
        channel.queueDeclare(queueName, true, true, false, args);
        verifyQueue(queueName, true, true, false, args);
    }
}
