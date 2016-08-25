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

import static org.junit.Assert.*;
import org.junit.Test;


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
import java.util.concurrent.TimeoutException;

public class Confirm extends BrokerTestCase
{
    private final static int NUM_MESSAGES = 1000;

    private static final String TTL_ARG = "x-message-ttl";

    @Override
    public void setUp() throws IOException, TimeoutException {
        super.setUp();
        channel.confirmSelect();
        channel.queueDeclare("confirm-test", true, true, false, null);
        channel.queueDeclare("confirm-durable-nonexclusive", true, false,
                             false, null);
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

    @Test public void persistentMandatoryCombinations()
        throws IOException, InterruptedException, TimeoutException {
        boolean b[] = { false, true };
        for (boolean persistent : b) {
            for (boolean mandatory : b) {
                confirmTest("", "confirm-test", persistent, mandatory);
            }
        }
    }

    @Test public void nonDurable()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("", "confirm-test-nondurable", true, false);
    }

    @Test public void mandatoryNoRoute()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("", "confirm-test-doesnotexist", false, true);
        confirmTest("", "confirm-test-doesnotexist",  true, true);
    }

    @Test public void multipleQueues()
        throws IOException, InterruptedException, TimeoutException {
        confirmTest("amq.direct", "confirm-multiple-queues", true, false);
    }

    /* For testQueueDelete and testQueuePurge to be
     * relevant, the msg_store must not write the messages to disk
     * (thus causing a confirm).  I'd manually comment out the line in
     * internal_sync that notifies the clients. */

    @Test public void queueDelete()
        throws IOException, InterruptedException, TimeoutException {
        publishN("","confirm-test-noconsumer", true, false);

        channel.queueDelete("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void queuePurge()
        throws IOException, InterruptedException, TimeoutException {
        publishN("", "confirm-test-noconsumer", true, false);

        channel.queuePurge("confirm-test-noconsumer");

        channel.waitForConfirmsOrDie(60000);
    }

    /* Tests rabbitmq-server #854 */
    @Test public void confirmQueuePurge()
        throws IOException, InterruptedException, TimeoutException {
        channel.basicQos(1);
        for (int i = 0; i < 20000; i++) {
            publish("", "confirm-durable-nonexclusive", true, false);
            if (i % 100 == 0) {
                channel.queuePurge("confirm-durable-nonexclusive");
            }
        }
        channel.waitForConfirmsOrDie(90000);
    }

    @Test public void basicReject()
        throws IOException, InterruptedException, TimeoutException {
        basicRejectCommon(false);

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void queueTTL()
        throws IOException, InterruptedException, TimeoutException {
        for (int ttl : new int[]{ 1, 0 }) {
            Map<String, Object> argMap =
                Collections.singletonMap(TTL_ARG, (Object)ttl);
            channel.queueDeclare("confirm-ttl", true, true, false, argMap);

            publishN("", "confirm-ttl", true, false);
            channel.waitForConfirmsOrDie(60000);

            channel.queueDelete("confirm-ttl");
        }
    }

    @Test public void basicRejectRequeue()
        throws IOException, InterruptedException, TimeoutException {
        basicRejectCommon(true);

        /* wait confirms to go through the broker */
        Thread.sleep(1000);

        channel.basicConsume("confirm-test-noconsumer", true,
                             new DefaultConsumer(channel));

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void basicRecover()
        throws IOException, InterruptedException, TimeoutException {
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

        channel.waitForConfirmsOrDie(60000);
    }

    @Test public void select()
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

    @Test public void waitForConfirms()
        throws IOException, InterruptedException, TimeoutException {
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

        channel.waitForConfirmsOrDie(60000);
        if (!unconfirmedSet.isEmpty()) {
            fail("waitForConfirms returned with unconfirmed messages");
        }
    }

    @Test public void waitForConfirmsWithoutConfirmSelected()
        throws IOException, InterruptedException
    {
        channel = connection.createChannel();
        // Don't enable Confirm mode
        publish("", "confirm-test", true, false);
        try {
            channel.waitForConfirms(60000);
            fail("waitForConfirms without confirms selected succeeded");
        } catch (IllegalStateException _e) {} catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Test public void waitForConfirmsException()
        throws IOException, InterruptedException, TimeoutException {
        publishN("", "confirm-test", true, false);
        channel.close();
        try {
            channel.waitForConfirmsOrDie(60000);
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
        throws IOException, InterruptedException, TimeoutException {
        publishN(exchange, queueName, persistent, mandatory);

        channel.waitForConfirmsOrDie(60000);
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
