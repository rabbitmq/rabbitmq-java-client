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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;

import java.io.IOException;

public abstract class PerMessageTTLHandling extends TTLHandling {

    protected Object sessionTTL;

    @Override
    protected void publish(String msg) throws IOException {
        basicPublishVolatile(msg.getBytes(), TTL_EXCHANGE, TTL_QUEUE_NAME,
                MessageProperties.TEXT_PLAIN
                        .builder()
                        .expiration(String.valueOf(sessionTTL))
                        .build());
    }

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        this.sessionTTL = ttlValue;
        return this.channel.queueDeclare(name, false, true, false, null);
    }

}
