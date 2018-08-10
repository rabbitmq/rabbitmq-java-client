// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import java.util.HashMap;

import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryHandlerBuilder.builder;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.CHANNEL_CLOSED_NOT_FOUND;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RECOVER_BINDING;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RECOVER_BINDING_QUEUE;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RECOVER_CHANNEL;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RECOVER_CONSUMER;
import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RECOVER_CONSUMER_QUEUE;
import static com.rabbitmq.client.test.TestUtils.closeAllConnectionsAndWaitForRecovery;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class TopologyRecoveryRetry extends BrokerTestCase {

    @Test
    public void topologyRecoveryRetry() throws Exception {
        int nbQueues = 2000;
        String prefix = "topology-recovery-retry-" + System.currentTimeMillis();
        for (int i = 0; i < nbQueues; i++) {
            String queue = prefix + i;
            channel.queueDeclare(queue, false, false, true, new HashMap<>());
            channel.queueBind(queue, "amq.direct", queue);
            channel.basicConsume(queue, true, new DefaultConsumer(channel));
        }

        closeAllConnectionsAndWaitForRecovery(this.connection);

        assertTrue(channel.isOpen());
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setTopologyRecoveryRetryHandler(
            builder().bindingRecoveryRetryCondition(CHANNEL_CLOSED_NOT_FOUND)
                .consumerRecoveryRetryCondition(CHANNEL_CLOSED_NOT_FOUND)
                .bindingRecoveryRetryOperation(RECOVER_CHANNEL.andThen(RECOVER_BINDING_QUEUE).andThen(RECOVER_BINDING))
                .consumerRecoveryRetryOperation(RECOVER_CHANNEL.andThen(RECOVER_CONSUMER_QUEUE.andThen(RECOVER_CONSUMER)))
                .build()
        );
        connectionFactory.setNetworkRecoveryInterval(1000);
        return connectionFactory;
    }
}
