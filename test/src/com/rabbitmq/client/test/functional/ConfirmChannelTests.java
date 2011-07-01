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
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmChannel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.DefaultConsumer;

import com.rabbitmq.client.test.ConfirmBase;

import java.io.IOException;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public class ConfirmChannelTests extends ConfirmBase
{
    private final static int NUM_MESSAGES = 100;
    private final static String QUEUE_NAME = "confirmchannel-test";

    private SortedSet<Long> unconfirmedSet;

    @Override
    protected void setUp() throws IOException {
        super.setUp();

        unconfirmedSet = Collections.synchronizedSortedSet(new TreeSet<Long>());
        channel.setConfirmListener(new ConfirmListener() {
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

        channel.queueDeclare(QUEUE_NAME, true, true, false, null);
        channel.basicConsume(QUEUE_NAME, true, new DefaultConsumer(channel));
    }

    public void testConfirmChannel()
        throws IOException, InterruptedException
    {
        for (long i = 0; i < NUM_MESSAGES; i++) {
            unconfirmedSet.add(channel.getNextPublishSeqNo());
            publish("", QUEUE_NAME, true, false, false);
        }
        if (((ConfirmChannel)channel).waitForConfirms() &&
            !unconfirmedSet.isEmpty())
        {
            fail("waitForConfirms returned with unconfirmed messages");
        }
    }
}
