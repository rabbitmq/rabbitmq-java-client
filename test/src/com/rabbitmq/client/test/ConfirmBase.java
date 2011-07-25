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

package com.rabbitmq.client.test;

import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public class ConfirmBase extends BrokerTestCase {

    protected SortedSet<Long> unconfirmedSet;

    @Override
    protected void setUp() throws IOException {
        super.setUp();
        unconfirmedSet = Collections.synchronizedSortedSet(new TreeSet<Long>());
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
        channel.confirmSelect();
    }

    protected void waitAcks() throws InterruptedException {
        while (unconfirmedSet.size() > 0)
            Thread.sleep(10);
    }

    protected void publish(String exchangeName, String queueName,
                           boolean persistent, boolean mandatory,
                           boolean immediate)
        throws IOException {
        unconfirmedSet.add(channel.getNextPublishSeqNo());
        channel.basicPublish(exchangeName, queueName, mandatory, immediate,
                             persistent ? MessageProperties.PERSISTENT_BASIC
                                        : MessageProperties.BASIC,
                             "nop".getBytes());
    }

}
