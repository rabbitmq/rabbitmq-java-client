//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AckListener;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class Confirm extends BrokerTestCase
{
    final static int NUM_MESSAGES = 1000;
    volatile Set<Long> ackSet;

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        ackSet = new TreeSet<Long>();
        final Confirm This = this;
        channel.setAckListener(new AckListener() {
                public void handleAck(long seqNo,
                                      boolean multiple) {
                    if (multiple) {
                        for (int i = 0; i <= seqNo; ++i)
                            This.gotAckFor(i);
                    } else {
                        This.gotAckFor(seqNo);
                    }
                }
            });
        channel.confirmSelect(true);
        channel.queueDeclare("confirm-test", true, true, true, null);
        channel.basicConsume("confirm-test", true, new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-nondurable", false, false, true, null);
        channel.basicConsume("confirm-test-nondurable", true,
                             new DefaultConsumer(channel));
        channel.queueDeclare("confirm-test-noconsumer", true, true, true, null);
        channel.queueDeclare("confirm-test-2", true, true, true, null);
        channel.basicConsume("confirm-test-2", true, new DefaultConsumer(channel));
        channel.queueBind("confirm-test", "amq.direct", "confirm-multiple-queues");
        channel.queueBind("confirm-test-2", "amq.direct", "confirm-multiple-queues");
    }

    public void testConfirmTransient() throws IOException, InterruptedException {
        confirmTest("confirm-test", false, false, false);
    }

    public void testConfirmPersistentSimple()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test", true, false, false);
    }

    public void testConfirmNonDurable()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test-nondurable", true, false, false);
    }

    public void testConfirmPersistentImmediate()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test", true, false, true);
    }

    public void testConfirmPersistentImmediateNoConsumer()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test-noconsumer", true, false, true);
    }

    public void testConfirmPersistentMandatory()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test", true, true, false);
    }

    public void testConfirmPersistentMandatoryReturn()
        throws IOException, InterruptedException
    {
        confirmTest("confirm-test-doesnotexist", true, true, false);
    }

    public void testConfirmMultipleQueues()
        throws IOException, InterruptedException
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            publish("amq.direct", "confirm-multiple-queues", true, false, false);
            ackSet.add(i);
        }

        while (ackSet.size() > 0)
            Thread.sleep(10);
    }

    /* Publish NUM_MESSAGES persistent messages and wait for
     * confirmations. */
    public void confirmTest(String queueName, boolean persistent,
                            boolean mandatory, boolean immediate)
        throws IOException, InterruptedException
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            publish(queueName, persistent, mandatory, immediate);
            ackSet.add(i);
        }

        while (ackSet.size() > 0)
            Thread.sleep(10);
    }

    private void publish(String queueName, boolean persistent,
                         boolean mandatory, boolean immediate)
        throws IOException
    {
        publish("", queueName, persistent, mandatory, immediate);
    }

    private void publish(String exchangeName, String queueName,
                         boolean persistent, boolean mandatory,
                         boolean immediate)
        throws IOException
    {
        channel.basicPublish(exchangeName, queueName, mandatory, immediate,
                             persistent ? MessageProperties.PERSISTENT_BASIC
                                        : MessageProperties.BASIC,
                             "nop".getBytes());
    }

    private synchronized void gotAckFor(long msgSeqNo) {
        if (!ackSet.contains(msgSeqNo))
            fail("got duplicate ack: " + msgSeqNo);
        ackSet.remove(msgSeqNo);
    }
}
