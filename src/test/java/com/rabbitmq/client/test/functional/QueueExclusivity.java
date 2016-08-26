// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.


package com.rabbitmq.client.test.functional;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.test.BrokerTestCase;

// Test queue auto-delete and exclusive semantics.
public class QueueExclusivity extends BrokerTestCase {

    final HashMap<String, Object> noArgs = new HashMap<String, Object>();

    public Connection altConnection;
    public Channel altChannel;
    final String q = "exclusiveQ";

    protected void createResources() throws IOException, TimeoutException {
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

    @Test public void queueExclusiveForPassiveDeclare() throws Exception {
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
    @Test public void queueExclusiveForDeclare() throws Exception {
        try {
            channel.queueDeclare(q, false, true, false, noArgs);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Active queue declaration of an exclusive queue from another connection should fail");
    }

    @Test public void queueExclusiveForConsume() throws Exception {
        QueueingConsumer c = new QueueingConsumer(channel);
        try {
            channel.basicConsume(q, c);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for basic consume from another connection");
    }

    @Test public void queueExclusiveForPurge() throws Exception {
        try {
            channel.queuePurge(q);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue purge from another connection");
    }

    @Test public void queueExclusiveForDelete() throws Exception {
        try {
            channel.queueDelete(q);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue delete from another connection");
    }

    @Test public void queueExclusiveForBind() throws Exception {
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

    @Test public void queueExclusiveForUnbind() throws Exception {
        altChannel.queueBind(q, "amq.direct", "");
        try {
            channel.queueUnbind(q, "amq.direct", "");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for queue unbind from another connection");
    }

    @Test public void queueExclusiveForGet() throws Exception {
        try {
            channel.basicGet(q, true);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.RESOURCE_LOCKED, ioe);
            return;
        }
        fail("Exclusive queue should be locked for basic get from another connection");
    }

}
