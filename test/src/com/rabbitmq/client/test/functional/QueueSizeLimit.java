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
    private final String q = "queue-maxdepth";

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        channel.confirmSelect();
    }

    private void setupDlx() throws IOException {
        channel.exchangeDeclare("DLX", "fanout");
        channel.queueDeclare("DLQ", false, true, false, null);
        channel.queueBind("DLQ", "DLX", "test");
    }

    private void tearDownDlx() throws IOException {
        channel.exchangeDelete("DLX");
        channel.queueDelete("DLQ");
    }

    public void testQueueSize() throws IOException, InterruptedException {
        declareQueue();
        fillUp();
        syncPublish(null, "msg" + MAXLENGTH + 1);
        assertEquals(MAXLENGTH, declareQueue());
        assertHead("msg2", q);
    }

    public void testQueueUnacked() throws IOException, InterruptedException {
        declareQueue();
        fillUnacked();
        syncPublish(null, "msg" + MAXLENGTH + 1);
        assertEquals(1, declareQueue());
        assertHead("msg" + MAXLENGTH + 1, q);
    }

    public void testPersistent() throws IOException, InterruptedException {
        declareQueue(true);
        fillUnacked(true);
        syncPublish(MessageProperties.MINIMAL_PERSISTENT_BASIC, "msg" + MAXLENGTH + 1);
        assertEquals(1, declareQueue(true));
        assertHead("msg" + MAXLENGTH + 1, q);
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
        declareQueue(persistent, true);
        setupDlx();
        AMQP.BasicProperties props = null;
        if (persistent)
            props = MessageProperties.MINIMAL_PERSISTENT_BASIC;
        fillUp(persistent);
        syncPublish(props, "msg" + MAXLENGTH + 1);
        assertEquals(MAXLENGTH, declareQueue(persistent));
        assertHead("msg1", "DLQ");
        assertNull(channel.basicGet("DLQ", true));
        tearDownDlx();
    }

    public void dlxTail(boolean persistent) throws IOException, InterruptedException {
        declareQueue(persistent, true);
        setupDlx();
        AMQP.BasicProperties props = null;
        if (persistent)
            props = MessageProperties.MINIMAL_PERSISTENT_BASIC;
        fillUnacked(persistent);
        syncPublish(props, "msg" + MAXLENGTH + 1);
        assertNull(null, channel.basicGet("DLQ", true));
        assertHead("msg" + MAXLENGTH + 1, q);
        assertNull(channel.basicGet(q, true));
        tearDownDlx();
    }

    private int declareQueue() throws IOException {
        return declareQueue(false, false);
    }

    private int declareQueue(boolean durable) throws IOException {
        return declareQueue(durable, false);
    }

    private void fillUp() throws IOException, InterruptedException {
        fill(false, false);
    }

    private void fillUp(boolean persistent) throws IOException, InterruptedException {
        fill(persistent, false);
    }

    private void fillUnacked() throws IOException, InterruptedException {
        fill(false, true);
    }

    private void fillUnacked(boolean persistent) throws IOException, InterruptedException {
        fill(persistent, true);
    }

    private void fill(boolean persistent, boolean unAcked) throws IOException, InterruptedException {
        for (int i=1; i <= MAXLENGTH; i++){
            syncPublish(null, "msg" + i);
            if (unAcked) {
                channel.basicGet(q, false);
                assertEquals(0, declareQueue(persistent));
            } else {
                assertEquals(i, declareQueue(persistent));
            }
        }
    }

    private void syncPublish(AMQP.BasicProperties props, String payload) throws IOException, InterruptedException {
        channel.basicPublish("", q, props, payload.getBytes());
        channel.waitForConfirmsOrDie();
    }

    private int declareQueue(boolean durable, boolean dlx) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-max-length", MAXLENGTH);
        if (dlx) args.put("x-dead-letter-exchange", "DLX");
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(q, durable, true, true, args);
        return ok.getMessageCount();
    }

    private void assertHead(String expected, String queueName) throws IOException {
        assertEquals(expected, new String(channel.basicGet(queueName, true).getBody()));
    }
}
