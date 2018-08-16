// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 *
 */
public class GeneratedClassesTest {

    @Test
    public void amqpPropertiesEqualsHashCode() {
        checkEquals(
            new AMQP.BasicProperties.Builder().correlationId("one").build(),
            new AMQP.BasicProperties.Builder().correlationId("one").build()
        );
        checkNotEquals(
            new AMQP.BasicProperties.Builder().correlationId("one").build(),
            new AMQP.BasicProperties.Builder().correlationId("two").build()
        );
        Date date = Calendar.getInstance().getTime();
        checkEquals(
            new AMQP.BasicProperties.Builder()
                .deliveryMode(1)
                .headers(singletonMap("one", "two"))
                .correlationId("123")
                .expiration("later")
                .priority(10)
                .replyTo("me")
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .userId("jdoe")
                .appId("app1")
                .clusterId("cluster1")
                .messageId("message123")
                .timestamp(date)
                .type("type")
                .build(),
            new AMQP.BasicProperties.Builder()
                .deliveryMode(1)
                .headers(singletonMap("one", "two"))
                .correlationId("123")
                .expiration("later")
                .priority(10)
                .replyTo("me")
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .userId("jdoe")
                .appId("app1")
                .clusterId("cluster1")
                .messageId("message123")
                .timestamp(date)
                .type("type")
                .build()
        );
        checkNotEquals(
            new AMQP.BasicProperties.Builder()
                .deliveryMode(1)
                .headers(singletonMap("one", "two"))
                .correlationId("123")
                .expiration("later")
                .priority(10)
                .replyTo("me")
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .userId("jdoe")
                .appId("app1")
                .clusterId("cluster1")
                .messageId("message123")
                .timestamp(date)
                .type("type")
                .build(),
            new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .headers(singletonMap("one", "two"))
                .correlationId("123")
                .expiration("later")
                .priority(10)
                .replyTo("me")
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .userId("jdoe")
                .appId("app1")
                .clusterId("cluster1")
                .messageId("message123")
                .timestamp(date)
                .type("type")
                .build()
        );

    }

    @Test public void amqImplEqualsHashCode() {
        checkEquals(
            new AMQImpl.Basic.Deliver("tag", 1L, false, "amq.direct","rk"),
            new AMQImpl.Basic.Deliver("tag", 1L, false, "amq.direct","rk")
        );
        checkNotEquals(
            new AMQImpl.Basic.Deliver("tag", 1L, false, "amq.direct","rk"),
            new AMQImpl.Basic.Deliver("tag", 2L, false, "amq.direct","rk")
        );
    }

    private void checkEquals(Object o1, Object o2) {
        assertEquals(o1, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
    }

    private void checkNotEquals(Object o1, Object o2) {
        assertNotEquals(o1, o2);
    }
}
