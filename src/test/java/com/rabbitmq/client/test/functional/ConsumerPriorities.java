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

import com.rabbitmq.client.*;
import com.rabbitmq.client.test.BrokerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConsumerPriorities extends BrokerTestCase {
    @Test public void validation() throws IOException {
        assertFailValidation(args("banana"));
        assertFailValidation(args(new HashMap<Object, Object>()));
        assertFailValidation(args(null));
        assertFailValidation(args(Arrays.asList(1, 2, 3)));
    }

    private void assertFailValidation(Map<String, Object> args) throws IOException {
        Channel ch = connection.createChannel();
        String queue = ch.queueDeclare().getQueue();
        try {
            ch.basicConsume(queue, true, args, new DefaultConsumer(ch));
            fail("Validation should fail for " + args);
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }

    private static final int COUNT = 10;

    @Test public void consumerPriorities() throws Exception {
        String queue = channel.queueDeclare().getQueue();
        QueueMessageConsumer highConsumer = new QueueMessageConsumer(channel);
        QueueMessageConsumer medConsumer = new QueueMessageConsumer(channel);
        QueueMessageConsumer lowConsumer = new QueueMessageConsumer(channel);
        String high = channel.basicConsume(queue, true, args(1), highConsumer);
        String med = channel.basicConsume(queue, true, medConsumer);
        channel.basicConsume(queue, true, args(-1), lowConsumer);

        publish(queue, COUNT, "high");
        channel.basicCancel(high);
        publish(queue, COUNT, "med");
        channel.basicCancel(med);
        publish(queue, COUNT, "low");

        assertContents(highConsumer, COUNT, "high");
        assertContents(medConsumer, COUNT, "med");
        assertContents(lowConsumer, COUNT, "low");
    }

    private Map<String, Object> args(Object o) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("x-priority", o);
        return map;
    }

    private void assertContents(QueueMessageConsumer c, int count, String msg) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            byte[] body = c.nextDelivery(100);
            assertEquals(msg, new String(body));
        }
        assertEquals(null, c.nextDelivery());
    }

    private void publish(String queue, int count, String msg) throws IOException {
        for (int i = 0; i < count; i++) {
            channel.basicPublish("", queue, MessageProperties.MINIMAL_BASIC, msg.getBytes());
        }
    }

    class QueueMessageConsumer extends DefaultConsumer {

        BlockingQueue<byte[]> messages = new LinkedBlockingQueue<byte[]>();

        public QueueMessageConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            messages.add(body);
        }

        byte[] nextDelivery() {
            return messages.poll();
        }

        byte[] nextDelivery(long timeoutInMs) throws InterruptedException {
            return messages.poll(timeoutInMs, TimeUnit.MILLISECONDS);
        }

    }

}
