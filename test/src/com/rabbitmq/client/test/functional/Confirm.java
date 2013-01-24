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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class Confirm extends BrokerTestCase
{
    private final static int NUM_MESSAGES = 1000;

    private static final String TTL_ARG = "x-message-ttl";

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        channel.confirmSelect();
        channel.queueDeclare("confirm-test", true, true, false, null);
        channel.basicConsume("confirm-test", true,
                             new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-nondurable", false, true,
                             false, null);
        channel.basicConsume("confirm-test-nondurable", true,
                             new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-noconsumer", true,
                             true, false, null);
        channel.queueDeclare("confirm-test-2", true, true, false, null);
        channel.basicConsume("confirm-test-2", true,
                             new DefaultConsumer(channel));
        channel.queueBind("confirm-test", "amq.direct",
                          "confirm-multiple-queues");
        channel.queueBind("confirm-test-2", "amq.direct",
                          "confirm-multiple-queues");
    }

    public void testPersistentMandatoryCombinations()
        throws IOException, InterruptedException
    {
        boolean b[] = { false, true };
        for (boolean persistent : b) {
            for (boolean mandatory : b) {
                confirmTest("", "confirm-test", persistent, mandatory);
            }
        }
    }

    public void testNonDurable()
        throws IOException, InterruptedException
    {
        confirmTest("", "confirm-test-nondurable", true, false);
    }

    public void testMandatoryNoRoute()
        throws IOException, InterruptedException
    {
        confirmTest("", "confirm-test-doesnotexist", false, true);
        confirmTest("", "confirm-test-doesnotexist",  true, true);
    }

    public void testMultipleQueues()
        throws IOException, InterruptedException
    {
        confirmTest("amq.direct", "confirm-multiple-queues", true, false);
    }

    /* For testQueueDelete and testQueuePurge to be
     * relevant, the msg_store must not write the messages to disk
     * (thus causing a confirm).  I'd manually comment out the line in
     * internal_sync that notifies the clients. */

    public void testQueueDelete()
        throws IOException, InterruptedException
    {
        publishN("","confirm-test-noconsumer", true, false);

        channel.queueDelete("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie();
    }

    public void testQueuePurge()
        throws IOException, InterruptedException
    {
        publishN("", "confirm-test-noconsumer", true, false);

        channel.queuePurge("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie();
    }

    public void testBasicReject()
        throws IOException, InterruptedException
    {
        basicRejectCommon(false);

        channel.waitForConfirmsOrDie();
    }

    public void testQueueTTL()
        throws IOException, InterruptedException
    {
        for (int ttl : new int[]{ 1, 0 }) {
            Map<String, Object> argMap =
                Collections.singletonMap(TTL_ARG, (Object)ttl);
            channel.queueDeclare("confirm-ttl", true, true, false, argMap);

            publishN("", "confirm-ttl", true, false);
            channel.waitForConfirmsOrDie();

            channel.queueDelete("confirm-ttl");
        }
    }

    public void testBasicRejectRequeue()
        throws IOException, InterruptedException
    {
        basicRejectCommon(true);

        /* wait confirms to go through the broker */
        Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             new DefaultConsumer(channel));

        channel.waitForConfirmsOrDie();
    }

    public void testBasicRecover()
        throws IOException, InterruptedException
    {
        publishN("", "confirm-test-noconsumer", true, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet("confirm-test-noconsumer", false);
            resp.getEnvelope().getDeliveryTag();
            // not acking
        }

        channel.basicRecover(true);

        Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             new DefaultConsumer(channel));

        channel.waitForConfirmsOrDie();
    }

    public void testSelect()
        throws IOException
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
        throws IOException, InterruptedException
    {
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
            publish("", "confirm-test", true, false);
        }

        channel.waitForConfirmsOrDie();
        if (!unconfirmedSet.isEmpty()) {
            fail("waitForConfirms returned with unconfirmed messages");
        }
    }

    public void testWaitForConfirmsNoOp()
        throws IOException, InterruptedException
    {
        channel = connection.createChannel();
        // Don't enable Confirm mode
        publish("", "confirm-test", true, false);
        channel.waitForConfirmsOrDie(); // Nop
    }

    public void testWaitForConfirmsException()
        throws IOException, InterruptedException
    {
        publishN("", "confirm-test", true, false);
        channel.close();
        try {
            channel.waitForConfirmsOrDie();
            fail("waitAcks worked on a closed channel");
        } catch (ShutdownSignalException sse) {
            if (!(sse.getReason() instanceof AMQP.Channel.Close))
                fail("Shutdown reason not Channel.Close");
            //whoosh; everything ok
        } catch (InterruptedException e) {
            // whoosh; we should probably re-run, though
        }
    }

    /* Publish NUM_MESSAGES messages and wait for confirmations. */
    public void confirmTest(String exchange, String queueName,
                            boolean persistent, boolean mandatory)
        throws IOException, InterruptedException
    {
        publishN(exchange, queueName, persistent, mandatory);

        channel.waitForConfirmsOrDie();
    }

    private void publishN(String exchangeName, String queueName,
                          boolean persistent, boolean mandatory)
        throws IOException
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            publish(exchangeName, queueName, persistent, mandatory);
        }
    }

    private void basicRejectCommon(boolean requeue)
        throws IOException
    {
        publishN("", "confirm-test-noconsumer", true, false);

        for (long i = 0; i < NUM_MESSAGES; i++) {
            GetResponse resp =
                channel.basicGet("confirm-test-noconsumer", false);
            long dtag = resp.getEnvelope().getDeliveryTag();
            channel.basicReject(dtag, requeue);
        }
    }

    protected void publish(String exchangeName, String queueName,
                           boolean persistent, boolean mandatory)
        throws IOException {
        channel.basicPublish(exchangeName, queueName, mandatory, false,
                             persistent ? MessageProperties.PERSISTENT_BASIC
                                        : MessageProperties.BASIC,
                             "nop".getBytes());
    }
}
