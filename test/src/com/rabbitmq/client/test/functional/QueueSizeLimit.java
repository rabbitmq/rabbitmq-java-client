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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test queue max length limit.
 */
public class QueueSizeLimit extends BrokerTestCase {

    private final int MAXLENGTH = 5;
    private final int MAXLENGTH1 = MAXLENGTH + 1;
    private final String q = "queue-maxlength";

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        channel.confirmSelect();
    }

    AMQP.BasicProperties setupDlx(boolean persistent) throws IOException {
        channel.queueDeclare("DLQ", false, true, false, null);
        channel.queueBind("DLQ", "amq.fanout", "");
        declareQueue(persistent, true);
        AMQP.BasicProperties props = null;
        if (persistent) {
            props = MessageProperties.MINIMAL_PERSISTENT_BASIC;
        }
        return props;
    }

    public void testQueueSize() throws IOException, InterruptedException {
        declareQueue(false, false);
        fill(false, false, false);
        syncPublish(null, "msg" + MAXLENGTH1);
        assertHead(MAXLENGTH, "msg2", q);
    }

    public void testQueueUnacked() throws IOException, InterruptedException {
        declareQueue(false, false);
        fill(false, true, false);
        syncPublish(null, "msg" + MAXLENGTH1);
        assertHead(1, "msg" + MAXLENGTH1, q);
    }

    public void testPersistent() throws IOException, InterruptedException {
        declareQueue(true, false);
        fill(true, true, false);
        syncPublish(MessageProperties.MINIMAL_PERSISTENT_BASIC, "msg" + MAXLENGTH1);
        assertHead(1, "msg" + MAXLENGTH1, q);
    }

    public void testDlxHeadTransient() throws IOException, InterruptedException {
        dlxHead(false);
    }

    public void testDlxTailTransient() throws IOException, InterruptedException {
        dlxTail(false);
    }

    public void testDlxHeadDurable() throws IOException, InterruptedException {
        dlxHead(true);
    }

    public void testDlxTailDurable() throws IOException, InterruptedException {
        dlxTail(true);
    }

    public void dlxHead(boolean persistent) throws IOException, InterruptedException {
        AMQP.BasicProperties props = setupDlx(persistent);
        fill(persistent, false, true);
        syncPublish(props, "msg" + MAXLENGTH1);
        assertEquals(MAXLENGTH, declareQueue(persistent, true));
        assertHead(1, "msg1", "DLQ");
    }

    public void dlxTail(boolean persistent) throws IOException, InterruptedException {
        AMQP.BasicProperties props = setupDlx(persistent);
        fill(persistent, true, true);
        syncPublish(props, "msg" + MAXLENGTH1);
        assertNull(channel.basicGet("DLQ", true));
        assertHead(1, "msg" + MAXLENGTH1, q);
    }

    private void fill(boolean persistent, boolean unAcked, boolean dlx) throws IOException, InterruptedException {
        for (int i=1; i <= MAXLENGTH; i++){
            syncPublish(null, "msg" + i);
            if (unAcked) {
                assertNotNull(channel.basicGet(q, false));
            }
        }
        if (unAcked) {
            assertEquals(0, declareQueue(persistent, dlx));
        } else {
            assertEquals(MAXLENGTH, declareQueue(persistent, dlx));
        }
    }

    private void syncPublish(AMQP.BasicProperties props, String payload) throws IOException, InterruptedException {
        channel.basicPublish("", q, props, payload.getBytes());
        channel.waitForConfirmsOrDie();
    }

    private int declareQueue(boolean persistent, boolean dlx) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-max-length", MAXLENGTH);
        if (dlx) {
            args.put("x-dead-letter-exchange", "amq.fanout");
        }
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(q, persistent, true, true, args);
        return ok.getMessageCount();
    }

    private void assertHead(int expectedLength, String expectedPayload, String queueName) throws IOException {
        GetResponse head = channel.basicGet(queueName, true);
        assertNotNull(head);
        assertEquals(expectedPayload, new String(head.getBody()));
        assertEquals(expectedLength, head.getMessageCount() + 1);
    }
}
