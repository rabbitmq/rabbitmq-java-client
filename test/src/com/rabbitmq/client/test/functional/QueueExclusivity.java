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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

// Test queue auto-delete and exclusive semantics.
public class QueueExclusivity extends BrokerTestCase {

    HashMap<String, Object> noArgs = new HashMap<String, Object>();

    public Connection altConnection;
    public Channel altChannel;
    String q = "exclusiveQ";

    protected void createResources() throws IOException {
        altConnection = connectionFactory.newConnection();
        altChannel = altConnection.createChannel();
        altChannel.queueDeclare(q,
        // not durable, exclusive, not auto-delete
                false, true, false, noArgs);
    }

    protected void releaseResources() throws IOException {
        if (altConnection != null && altConnection.isOpen()) {
            altConnection.close();
        }
    }

    public void testQueueExclusiveForPassiveDeclare() throws Exception {
        try {
            channel.queueDeclarePassive(q);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Passive queue declaration of an exclusive queue from another connection should fail");
    }

    // This is a different scenario because active declare takes notice of
    // the all the arguments
    public void testQueueExclusiveForDeclare() throws Exception {
        try {
            channel.queueDeclare(q, false, true, false, noArgs);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Active queue declaration of an exclusive queue from another connection should fail");
    }

    public void testQueueExclusiveForConsume() throws Exception {
        QueueingConsumer c = new QueueingConsumer(channel);
        try {
            channel.basicConsume(q, c);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for basic consume from another connection");
    }

    public void testQueueExclusiveForPurge() throws Exception {
        try {
            channel.queuePurge(q);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue purge from another connection");
    }

    public void testQueueExclusiveForDelete() throws Exception {
        try {
            channel.queueDelete(q);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue delete from another connection");
    }

    public void testQueueExclusiveForBind() throws Exception {
        try {
            channel.queueBind(q, "amq.direct", "");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue bind from another connection");
    }

    // NB The spec XML doesn't mention queue.unbind, basic.cancel, or
    // basic.get in the exclusive rule. It seems the most sensible
    // interpretation to include queue.unbind and basic.get in the
    // prohibition.
    // basic.cancel is inherently local to a channel, so it
    // *doesn't* make sense to include it.

    public void testQueueExclusiveForUnbind() throws Exception {
        altChannel.queueBind(q, "amq.direct", "");
        try {
            channel.queueUnbind(q, "amq.direct", "");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue unbind from another connection");
    }

    public void testQueueExclusiveForGet() throws Exception {
        try {
            channel.basicGet(q, true);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for basic get from another connection");
    }

}
