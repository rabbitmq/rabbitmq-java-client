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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class PerQueueTTL extends TTLHandling {

    protected static final String TTL_ARG = "x-message-ttl";

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        Map<String, Object> argMap = Collections.singletonMap(TTL_ARG, ttlValue);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    public void testQueueReDeclareEquivalence() throws Exception {
        declareQueue(10);
        try {
            declareQueue(20);
            fail("Should not be able to redeclare with different x-message-ttl");
        } catch(IOException ex) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ex);
        }
    }

    public void testQueueReDeclareSemanticEquivalence() throws Exception {
        declareQueue((byte)10);
        declareQueue(10);
        declareQueue((short)10);
        declareQueue(10L);
    }

    public void testQueueReDeclareSemanticNonEquivalence() throws Exception {
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