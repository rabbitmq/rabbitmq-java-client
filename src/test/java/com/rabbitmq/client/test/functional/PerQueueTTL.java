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
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;

public class PerQueueTTL extends TTLHandling {

    protected static final String TTL_ARG = "x-message-ttl";

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        Map<String, Object> argMap = Collections.singletonMap(TTL_ARG, ttlValue);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    @Test public void queueReDeclareEquivalence() throws Exception {
        declareQueue(10);
        try {
            declareQueue(20);
            fail("Should not be able to redeclare with different x-message-ttl");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    @Test public void queueReDeclareSemanticEquivalence() throws Exception {
        declareQueue((byte)10);
        declareQueue(10);
        declareQueue((short)10);
        declareQueue(10L);
    }

    @Test public void queueReDeclareSemanticNonEquivalence() throws Exception {
        declareQueue(10);
        try {
            declareQueue(10.0);
            fail("Should not be able to redeclare with x-message-ttl argument of different type");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    protected void publishWithExpiration(String msg, Object sessionTTL) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME,
                MessageProperties.TEXT_PLAIN
                        .builder()
                        .expiration(String.valueOf(sessionTTL))
                        .build());
    }
}
