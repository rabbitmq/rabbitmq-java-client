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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;

public class DurableOnTransient extends ClusteredTestBase
{
    protected static final String Q = "DurableQueue";
    protected static final String X = "TransientExchange";

    private GetResponse basicGet()
        throws IOException
    {
        return channel.basicGet(Q, true);
    }

    private void basicPublish()
        throws IOException
    {
        channel.basicPublish(X, "",
                             MessageProperties.PERSISTENT_TEXT_PLAIN,
                             "persistent message".getBytes());
    }

    protected void createResources() throws IOException {
        // Transient exchange
        channel.exchangeDeclare(X, "direct", false);
        // durable queue
        channel.queueDeclare(Q, true, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
        channel.exchangeDelete(X);
    }

    public void testBindDurableToTransient()
        throws IOException
    {
        channel.queueBind(Q, X, "");
        basicPublish();
        assertNotNull(basicGet());
    }

    public void testSemiDurableBindingRemoval() throws IOException {
        if (clusteredConnection != null) {
            declareTransientTopicExchange("x");
            clusteredChannel.queueDeclare("q", true, false, false, null);
            channel.queueBind("q", "x", "k");

            stopSecondary();

            deleteExchange("x");

            startSecondary();

            declareTransientTopicExchange("x");

            basicPublishVolatile("x", "k");
            assertDelivered("q", 0);

            deleteQueue("q");
            deleteExchange("x");
        }
    }
}
