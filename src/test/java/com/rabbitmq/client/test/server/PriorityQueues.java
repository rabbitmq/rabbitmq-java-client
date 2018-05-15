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

package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.test.BrokerTestCase;

public class PriorityQueues extends BrokerTestCase {
    @Test public void prioritisingBasics() throws IOException, TimeoutException, InterruptedException {
        String q = "with-3-priorities";
        int n = 3;
        channel.queueDeclare(q, true, false, false, argsWithPriorities(n));
        publishWithPriorities(q, n);

        List<Integer> xs = prioritiesOfEnqueuedMessages(q, n);
        assertEquals(Integer.valueOf(3), xs.get(0));
        assertEquals(Integer.valueOf(2), xs.get(1));
        assertEquals(Integer.valueOf(1), xs.get(2));

        channel.queueDelete(q);
    }

    @Test public void negativeMaxPriority() throws IOException, TimeoutException, InterruptedException {
        String q = "with-minus-10-priorities";
        int n = -10;
        try {
            channel.queueDeclare(q, true, false, false, argsWithPriorities(n));
            fail("Negative priority, the queue creation should have failed");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }

    @Test public void excessiveMaxPriority() throws IOException, TimeoutException, InterruptedException {
        String q = "with-260-priorities";
        int n = 260;
        try {
            channel.queueDeclare(q, true, false, false, argsWithPriorities(n));
            fail("Priority too high (> 255), the queue creation should have failed");
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }

    @Test public void maxAllowedPriority() throws IOException, TimeoutException, InterruptedException {
        String q = "with-255-priorities";
        int n = 255;
        channel.queueDeclare(q, true, false, false, argsWithPriorities(n));
        publishWithPriorities(q, n);

        List<Integer> xs = prioritiesOfEnqueuedMessages(q, n);
        assertEquals(Integer.valueOf(255), xs.get(0));
        assertEquals(Integer.valueOf(254), xs.get(1));
        assertEquals(Integer.valueOf(253), xs.get(2));

        channel.queueDelete(q);
    }

    private List<Integer> prioritiesOfEnqueuedMessages(String q, int n) throws InterruptedException, IOException {
        final List<Integer> xs = new ArrayList<Integer>();
        final CountDownLatch latch = new CountDownLatch(n);

        channel.basicConsume(q, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                xs.add(properties.getPriority());
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        return xs;
    }

    private void publishWithPriorities(String q, int n) throws IOException, TimeoutException, InterruptedException {
        channel.confirmSelect();
        for (int i = 1; i <= n; i++) {
            channel.basicPublish("", q, propsWithPriority(i), "msg".getBytes("UTF-8"));
        }
        channel.waitForConfirms(500);
    }

    private AMQP.BasicProperties propsWithPriority(int n) {
        return (new AMQP.BasicProperties.Builder())
                .priority(n)
                .build();
    }

    private Map<String, Object> argsWithPriorities(int n) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("x-max-priority", n);
        return m;
    }
}
