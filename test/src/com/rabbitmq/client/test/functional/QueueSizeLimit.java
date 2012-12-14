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
 * Test queue maxdepth limit.
 */
public class QueueSizeLimit extends BrokerTestCase {

    private final int MAXDEPTH = 5;
    private final String q = "queue";

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
        syncPublish(null, "overflow");
        assertEquals(MAXDEPTH, declareQueue());
    }

    public void testQueueAllUnacked() throws IOException, InterruptedException {
        declareQueue();
        fillUnacked();
        syncPublish(null, "overflow");
        assertEquals(null, channel.basicGet(q, true));
    }

    public void testQueueSomeUnacked() throws IOException, InterruptedException {
        declareQueue();
        fillUnacked(false, MAXDEPTH - 1);
        syncPublish(null, "msg" + MAXDEPTH);
        assertEquals(1, declareQueue());

        syncPublish(null, "overflow");
        assertEquals("overflow", new String(channel.basicGet(q, true).getBody()));
        assertEquals(null, channel.basicGet(q, true));
    }

    public void testConfirmPersistent() throws IOException, InterruptedException {
        declareQueue(true);
        fillUnacked(true, MAXDEPTH);
        syncPublish(MessageProperties.MINIMAL_PERSISTENT_BASIC, "overflow");
        assertEquals(null, channel.basicGet(q, false));
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
        syncPublish(props, "overflow");
        assertEquals(MAXDEPTH, declareQueue(persistent));
        assertEquals("msg1", new String(channel.basicGet("DLQ", true).getBody()));
        assertNull(channel.basicGet("DLQ", true));
        tearDownDlx();
    }

    public void dlxTail(boolean persistent) throws IOException, InterruptedException {
        declareQueue(persistent, true);
        setupDlx();
        AMQP.BasicProperties props = null;
        if (persistent)
            props = MessageProperties.MINIMAL_PERSISTENT_BASIC;
        fillUnacked(persistent, MAXDEPTH);
        syncPublish(props, "overflow");
        assertEquals(null, channel.basicGet(q, false));
        assertEquals("overflow", new String(channel.basicGet("DLQ", true).getBody()));
        assertNull(channel.basicGet("DLQ", true));
        tearDownDlx();
    }

    private int declareQueue() throws IOException {
        return declareQueue(false, false);
    }

    private int declareQueue(boolean durable) throws IOException {
        return declareQueue(durable, false);
    }

    private void fillUp() throws IOException, InterruptedException {
        fillUp(false);
    }

    private void fillUp(boolean persistent) throws IOException, InterruptedException {
        for (int i=1; i <= MAXDEPTH; i++){
            syncPublish(null, "msg" + i);
            assertEquals(i, declareQueue(persistent));
        }
    }

    private void fillUnacked() throws IOException, InterruptedException {
        fillUnacked(false, MAXDEPTH);
    }

    private void fillUnacked(boolean persistent, int depth) throws IOException, InterruptedException {
        for (int i=1; i <= depth; i++){
            syncPublish(null, "msg" + i);
            channel.basicGet(q, false);
            assertEquals(0, declareQueue(persistent));
        }
    }

    private void syncPublish(AMQP.BasicProperties props, String payload) throws IOException, InterruptedException {
        channel.basicPublish("", q, props, payload.getBytes());
        channel.waitForConfirmsOrDie();
    }

    private int declareQueue(boolean durable, boolean dlx) throws IOException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-maxdepth", MAXDEPTH);
        if (dlx) args.put("x-dead-letter-exchange", "DLX");
        AMQP.Queue.DeclareOk ok = channel.queueDeclare(q, durable, true, true, args);
        return ok.getMessageCount();
    }
}
