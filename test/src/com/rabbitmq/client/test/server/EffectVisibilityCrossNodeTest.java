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

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.functional.ClusteredTestBase;

import java.io.IOException;

/**
 * From bug 19844 - we want to be sure that publish vs everything else can't
 * happen out of order
 */
public class EffectVisibilityCrossNodeTest extends ClusteredTestBase {
    private String[] queues = new String[QUEUES];

    @Override
    protected void createResources() throws IOException {
        for (int i = 0; i < queues.length ; i++) {
            queues[i] = alternateChannel.queueDeclare("", false, false, true, null).getQueue();
            alternateChannel.queueBind(queues[i], "amq.fanout", "");
        }
    }

    @Override
    protected void releaseResources() throws IOException {
        for (int i = 0; i < queues.length ; i++) {
            alternateChannel.queueDelete(queues[i]);
        }
    }

    private static final int QUEUES = 5;
    private static final int BATCHES = 500;
    private static final int MESSAGES_PER_BATCH = 10;

    private static final byte[] msg = "".getBytes();

    public void testEffectVisibility() throws Exception {

        for (int i = 0; i < BATCHES; i++) {
            for (int j = 0; j < MESSAGES_PER_BATCH; j++) {
                channel.basicPublish("amq.fanout", "", null, msg);
            }
            for (int j = 0; j < queues.length ; j++) {
                assertEquals(MESSAGES_PER_BATCH, channel.queuePurge(queues[j]).getMessageCount());
            }
        }
    }
}
