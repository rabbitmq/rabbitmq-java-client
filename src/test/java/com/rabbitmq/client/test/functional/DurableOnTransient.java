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

import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;

public class DurableOnTransient extends ClusteredTestBase
{
    protected static final String Q = "SemiDurableBindings.DurableQueue";
    protected static final String X = "SemiDurableBindings.TransientExchange";

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
        channel.exchangeDelete(X);
        // transient exchange
        channel.exchangeDeclare(X, "direct", false);

        channel.queueDelete(Q);
        // durable queue
        channel.queueDeclare(Q, true, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q);
        channel.exchangeDelete(X);
    }

    @Test public void bindDurableToTransient()
        throws IOException
    {
        channel.queueBind(Q, X, "");
        basicPublish();
        assertNotNull(basicGet());
    }

    @Test public void semiDurableBindingRemoval() throws IOException {
        if (clusteredConnection != null) {
            deleteExchange("x");
            declareTransientTopicExchange("x");
            clusteredChannel.queueDelete("q");
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
