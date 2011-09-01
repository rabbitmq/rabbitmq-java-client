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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.ConfirmBase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class Confirm extends ConfirmBase
{
    private final static int NUM_MESSAGES = 1000;

    private static final String TTL_ARG = "x-message-ttl";

    private DefaultConsumer defaultConsumer = null;

    @Override
    protected void createResources() throws IOException {
        channel.confirmSelect();
        defaultConsumer = new DefaultConsumer(channel);
    }

    @Override
    protected void releaseResources() throws IOException {
        defaultConsumer = null;
    }

    private void declareQueue(String queueName, boolean durable)
    throws IOException {
        declareQueue(queueName, durable, null);
    }

    private void declareQueue(String queueName, boolean durable,
                              Map<String, Object> args)
    throws IOException {
        channel.queueDeclare(queueName, durable, true, false, args);
    }

    private void declareConsumeQueue(String queueName, boolean durable)
    throws IOException {
        declareQueue(queueName, durable);
        channel.basicConsume(queueName, true, defaultConsumer);
    }

    private void declareBindQueue(String queueName, boolean durable)
    throws IOException {
        declareConsumeQueue(queueName, durable);
        channel.queueBind(queueName, "amq.direct", "confirm-multiple-queues");
    }

    public void testPersistentMandatory()
    throws Exception
    {
        declareConsumeQueue("confirm-test", true);
        confirmTest("", "confirm-test", true, true, false);
    }

    public void testTransient()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);
        confirmTest("", "confirm-test", false, false, false);
    }

    public void testPersistentSimple()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);
        confirmTest("", "confirm-test", true, false, false);
    }

    public void testNonDurable()
        throws Exception
    {
        declareConsumeQueue("confirm-test-nondurable", false);
        confirmTest("", "confirm-test-nondurable", true, false, false);
    }

    public void testPersistentImmediate()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);
        confirmTest("", "confirm-test", true, false, true);
    }

    public void testPersistentImmediateNoConsumer()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);
        confirmTest("", "confirm-test-noconsumer", true, false, true);
    }

    public void testPersistentMandatoryReturn()
        throws Exception
    {
        confirmTest("", "confirm-test-doesnotexist", true, true, false);
    }

    public void testMultipleQueues()
        throws Exception
    {
        declareBindQueue("confirm-test", true);
        declareBindQueue("confirm-test-2", true);
        confirmTest("amq.direct", "confirm-multiple-queues",
                    true, false, false);
    }

    /* For testQueueDelete and testQueuePurge to be
     * relevant, the msg_store must not write the messages to disk
     * (thus causing a confirm).  I'd manually comment out the line in
     * internal_sync that notifies the clients. */

    public void testQueueDelete()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);

        publishN("","confirm-test-noconsumer", true, false, false);

        channel.queueDelete("confirm-test-noconsumer");

        waitForConfirms();
    }

    public void testQueuePurge()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);

        publishN("", "confirm-test-noconsumer", true, false, false);

        channel.queuePurge("confirm-test-noconsumer");

        waitForConfirms();
    }

    public void testBasicReject()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);

        basicRejectCommon("confirm-test-noconsumer", false);

        waitForConfirms();
    }

    public void testQueueTTL()
        throws Exception
    {
        declareQueue("confirm-ttl", true,
                Collections.singletonMap(TTL_ARG, (Object)Long.valueOf(1L)));

        publishN("", "confirm-ttl", true, false, false);

        waitForConfirms();
    }

    public void testBasicRejectRequeue()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);

        basicRejectCommon("confirm-test-noconsumer", true);

        /* wait confirms to go through the broker */
        //Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             defaultConsumer);

        waitForConfirms();
    }

    public void testBasicRecover()
        throws Exception
    {
        declareQueue("confirm-test-noconsumer", true);

        publishN("", "confirm-test-noconsumer", true, false, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet("confirm-test-noconsumer", false);
            resp.getEnvelope().getDeliveryTag();
            // not acking
        }

        channel.basicRecover(true);

        //Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             defaultConsumer);

        waitForConfirms();
    }

    public void testSelect()
        throws Exception
    {
        channel.confirmSelect();
        try {
            Channel ch = connection.createChannel();
            ch.confirmSelect();
            ch.txSelect();
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
        try {
            Channel ch = connection.createChannel();
            ch.txSelect();
            ch.confirmSelect();
            fail();
        } catch (IOException ioe) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, ioe);
        }
    }

    public void testWaitForConfirms()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);

        final SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());
        channel.addConfirmListener(new ConfirmListener() {
                public void handleAck(long seqNo, boolean multiple) {
                    if (!unconfirmedSet.contains(seqNo)) {
                        fail("got duplicate ack: " + seqNo);
                    }
                    if (multiple) {
                        unconfirmedSet.headSet(seqNo + 1).clear();
                    } else {
                        unconfirmedSet.remove(seqNo);
                    }
                }

                public void handleNack(long seqNo, boolean multiple) {
                    fail("got a nack");
                }
            });

        for (long i = 0; i < NUM_MESSAGES; i++) {
            unconfirmedSet.add(channel.getNextPublishSeqNo());
            publish("", "confirm-test", true, false, false);
        }

        waitForConfirms();
        if (!unconfirmedSet.isEmpty()) {
            fail("waitForConfirms returned with unconfirmed messages");
        }
    }

    public void testWaitForConfirmsNoOp()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);

        channel = connection.createChannel();
        // Don't enable Confirm mode
        publish("", "confirm-test", true, false, false);
        waitForConfirms(); // Nop
    }

    public void testWaitForConfirmsException()
        throws Exception
    {
        declareConsumeQueue("confirm-test", true);

        publishN("", "confirm-test", true, false, false);
        channel.close();
        try {
            waitForConfirms();
            fail("waitAcks worked on a closed channel");
        } catch (ShutdownSignalException sse) {
            if (!(sse.getReason() instanceof AMQP.Channel.Close))
                fail("didn't except for the right reason");
            //whoosh; everything ok
        } catch (InterruptedException e) {
            // whoosh; we should probably re-run, though
        }
    }

    /* Publish NUM_MESSAGES messages and wait for confirmations. */
    private void confirmTest(String exchange, String queueName,
                             boolean persistent, boolean mandatory,
                             boolean immediate)
        throws Exception
    {
        publishN(exchange, queueName, persistent, mandatory, immediate);

        waitForConfirms();
    }

    private void publishN(String exchangeName, String queueName,
                          boolean persistent, boolean mandatory,
                          boolean immediate)
        throws Exception
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            publish(exchangeName, queueName, persistent, mandatory, immediate);
        }
    }

    private void basicRejectCommon(String queueName, boolean requeue)
        throws Exception
    {
        publishN("", queueName, true, false, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet(queueName, false);
            long dtag = resp.getEnvelope().getDeliveryTag();
            channel.basicReject(dtag, requeue);
        }
    }

    private void publish(String exchangeName, String queueName,
                           boolean persistent, boolean mandatory,
                           boolean immediate)
        throws Exception
    {
        channel.basicPublish(exchangeName, queueName, mandatory, immediate,
                             persistent ? MessageProperties.PERSISTENT_BASIC
                                        : MessageProperties.BASIC,
                             "nop".getBytes());
    }
}
