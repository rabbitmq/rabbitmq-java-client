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

import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.rabbitmq.client.AMQP;

public class PerQueueVsPerMessageTTL extends PerMessageTTL {

    @Test public void smallerPerQueueExpiryWins() throws IOException, InterruptedException {
        declareAndBindQueue(10);
        this.sessionTTL = 1000;

        publish("message1");

        Thread.sleep(100);

        assertNull("per-queue ttl should have removed message after 10ms!", get());
    }

    @Override
    protected AMQP.Queue.DeclareOk declareQueue(String name, Object ttlValue) throws IOException {
        final Object mappedTTL = (ttlValue instanceof String &&
                                    ((String) ttlValue).contains("foobar")) ?
                                    ttlValue : longValue(ttlValue) * 2;
        this.sessionTTL = ttlValue;
        Map<String, Object> argMap = Collections.singletonMap(PerQueueTTL.TTL_ARG, mappedTTL);
        return this.channel.queueDeclare(name, false, true, false, argMap);
    }

    private Long longValue(final Object ttl) {
        if (ttl instanceof Short) {
            return ((Short)ttl).longValue();
        } else if (ttl instanceof Integer) {
            return ((Integer)ttl).longValue();
        } else if (ttl instanceof Long) {
            return (Long) ttl;
        } else {
            throw new IllegalArgumentException("ttl not of expected type");
        }
    }

}
