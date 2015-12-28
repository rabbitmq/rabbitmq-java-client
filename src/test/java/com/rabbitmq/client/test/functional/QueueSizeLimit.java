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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test queue max length limit.
 */
public class QueueSizeLimit extends BrokerTestCase {

    private final int MAXMAXLENGTH = 3;
    private final String q = "queue-maxlength";

    public void testQueueSize()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupNonDlxTest(maxLen, false);
            assertHead(maxLen, "msg2", q);
            deleteQueue(q);
        }
    }

    public void testQueueSizeUnacked()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupNonDlxTest(maxLen, true);
            assertHead(maxLen > 0 ? 1 : 0, "msg" + (maxLen + 1), q);
            deleteQueue(q);
        }
    }

    public void testQueueSizeDlx()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupDlxTest(maxLen, false);
            assertHead(1, "msg1", "DLQ");
            deleteQueue(q);
            deleteQueue("DLQ");
        }
    }

    public void testQueueSizeUnackedDlx()  throws IOException, InterruptedException {
        for (int maxLen = 0; maxLen <= MAXMAXLENGTH; maxLen ++){
            setupDlxTest(maxLen, true);
            assertHead(maxLen > 0 ? 0 : 1, "msg1", "DLQ");
            deleteQueue(q);
            deleteQueue("DLQ");
        }
    }

    public void testRequeue() throws IOException, InterruptedException  {
        for (int maxLen = 1; maxLen <= MAXMAXLENGTH; maxLen ++) {
            declareQueue(maxLen, false);
            setupRequeueTest(maxLen);
            assertHead(maxLen, "msg1", q);
            deleteQueue(q);
        }
    }

    public void testRequeueWithDlx() throws IOException, InterruptedException  {
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
