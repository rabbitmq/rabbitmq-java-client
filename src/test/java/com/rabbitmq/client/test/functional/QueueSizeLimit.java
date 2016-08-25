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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * Test queue max length limit.
 */
public class QueueSizeLimit extends BrokerTestCase {

    private final int MAXMAXLENGTH = 3;
    private final String q = "queue-maxlength";

    @Test public void queueSize()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupNonDlxTest(maxLen, false);
            assertHead(maxLen, "msg2", q);
            deleteQueue(q);
        }
    }

    @Test public void queueSizeUnacked()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupNonDlxTest(maxLen, true);
            assertHead(maxLen > 0 ? 1 : 0, "msg" + (maxLen + 1), q);
            deleteQueue(q);
        }
    }

    @Test public void queueSizeDlx()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupDlxTest(maxLen, false);
            assertHead(1, "msg1", "DLQ");
            deleteQueue(q);
            deleteQueue("DLQ");
        }
    }

    @Test public void queueSizeUnackedDlx()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupDlxTest(maxLen, true);
            assertHead(maxLen > 0 ? 0 : 1, "msg1", "DLQ");
            deleteQueue(q);
            deleteQueue("DLQ");
        }
    }

    @Test public void requeue() throws IOException, InterruptedException  {
        for (int maxLen = 1; maxLen <= MAXMAXLENGTH; maxLen ++) {
            declareQueue(maxLen, false);
            setupRequeueTest(maxLen);
            assertHead(maxLen, "msg1", q);
            deleteQueue(q);
        }
    }

    @Test public void requeueWithDlx() throws IOException, InterruptedException  {
        for (int maxLen = 1; maxLen <= MAXMAXLENGTH; maxLen ++) {
            declareQueue(maxLen, true);
            setupRequeueTest(maxLen);
            assertHead(maxLen, "msg1", q);
            assertHead(maxLen, "msg1", "DLQ");
            deleteQueue(q);
            deleteQueue("DLQ");
        }
    }

    private void setupNonDlxTest(int maxLen, boolean unAcked) throws IOException, InterruptedException {
        declareQueue(maxLen, false);
        fill(maxLen);
        if (unAcked) getUnacked(maxLen);
        publish("msg" + (maxLen + 1));
    }

    private void setupDlxTest(int maxLen, boolean unAcked) throws IOException, InterruptedException {
        declareQueue(maxLen, true);
        fill(maxLen);
        if (unAcked) getUnacked(maxLen);
        publish("msg" + (maxLen + 1));
        try {
            Thread.sleep(100);
        } catch (InterruptedException _e) { }
    }

    private void setupRequeueTest(int maxLen) throws IOException, InterruptedException {
        fill(maxLen);
        List<Long> tags = getUnacked(maxLen);
        fill(maxLen);
        channel.basicNack(tags.get(0), false, true);
        if (maxLen > 1)
            channel.basicNack(tags.get(maxLen - 1), true, true);
    }

    private void declareQueue(int maxLen, boolean dlx) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-max-length", maxLen);
        if (dlx) {
            args.put("x-dead-letter-exchange", "amq.fanout");
            channel.queueDeclare("DLQ", false, true, false, null);
            channel.queueBind("DLQ", "amq.fanout", "");
        }
        channel.queueDeclare(q, false, true, true, args);
    }

    private void fill(int count) throws IOException, InterruptedException {
        for (int i=1; i <= count; i++){
            publish("msg" + i);
        }
    }

    private void publish(String payload) throws IOException, InterruptedException {
        basicPublishVolatile(payload.getBytes(), q);
    }

    private void assertHead(int expectedLength, String expectedHeadPayload, String queueName) throws IOException {
        GetResponse head = channel.basicGet(queueName, true);
        if (expectedLength > 0) {
            assertNotNull(head);
            assertEquals(expectedHeadPayload, new String(head.getBody()));
            assertEquals(expectedLength, head.getMessageCount() + 1);
        } else {
            assertNull(head);
        }
    }

    private List<Long> getUnacked(int howMany) throws IOException {
        List<Long> tags = new ArrayList<Long>(howMany);
        for (;howMany > 0; howMany --) {
            GetResponse response = channel.basicGet(q, false);
            tags.add(response.getEnvelope().getDeliveryTag());
        }
        return tags;
    }
}
