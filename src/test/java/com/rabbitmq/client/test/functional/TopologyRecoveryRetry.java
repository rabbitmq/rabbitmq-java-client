// Copyright (c) 2018-2023 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.impl.recovery.RecordedBinding;
import com.rabbitmq.client.impl.recovery.RecordedConsumer;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.rabbitmq.client.impl.recovery.TopologyRecoveryRetryLogic.RETRY_ON_QUEUE_NOT_FOUND_RETRY_HANDLER;
import static com.rabbitmq.client.test.TestUtils.closeAllConnectionsAndWaitForRecovery;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class TopologyRecoveryRetry extends BrokerTestCase {

    private volatile Consumer<Integer> backoffConsumer;

    @BeforeEach
    public void init() {
        this.backoffConsumer = attempt -> { };
    }

    @Test
    public void topologyRecoveryRetry() throws Exception {
        int nbQueues = 200;
        String prefix = "topology-recovery-retry-" + System.currentTimeMillis();
        for (int i = 0; i < nbQueues; i++) {
            String queue = prefix + i;
            channel.queueDeclare(queue, false, false, true, new HashMap<>());
            channel.queueBind(queue, "amq.direct", queue);
            channel.queueBind(queue, "amq.direct", queue + "2");
            channel.basicConsume(queue, true, new DefaultConsumer(channel));
        }

        closeAllConnectionsAndWaitForRecovery(this.connection);

        assertTrue(channel.isOpen());
    }
    
    @Test
    public void topologyRecoveryBindingFailure() throws Exception {
        final String queue = "topology-recovery-retry-binding-failure" + System.currentTimeMillis();
        channel.queueDeclare(queue, false, false, true, new HashMap<>());
        channel.queueBind(queue, "amq.topic", "topic1");
        channel.queueBind(queue, "amq.topic", "topic2");
        final CountDownLatch messagesReceivedLatch = new CountDownLatch(2);
        channel.basicConsume(queue, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
                messagesReceivedLatch.countDown();
            }
        });
        final CountDownLatch recoveryLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addRecoveryListener(new RecoveryListener() {
            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                // no-op
            }
            @Override
            public void handleRecovery(Recoverable recoverable) {
                recoveryLatch.countDown();
            }
        });
        
        // we want recovery to fail when recovering the 2nd binding
        // give the 2nd recorded binding a bad queue name so it fails
        final RecordedBinding binding2 = ((AutorecoveringConnection)connection).getRecordedBindings().get(1);
        binding2.destination(UUID.randomUUID().toString());
        
        // use the backoffConsumer to know that it has failed
        // then delete the real queue & fix the recorded binding
        // it should fail once more because queue is gone, and then succeed
        final CountDownLatch backoffLatch = new CountDownLatch(1);
        backoffConsumer = attempt -> {
            if (attempt == 1) {
                binding2.destination(queue);
                try {
                    Host.rabbitmqctl("delete_queue " + queue);
                    Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            backoffLatch.countDown();
        };
        
        // close connection
        Host.closeAllConnections();
        
        // assert backoff was called
        assertTrue(backoffLatch.await(90, TimeUnit.SECONDS));
        // wait for full recovery
        assertTrue(recoveryLatch.await(90, TimeUnit.SECONDS));
        
        // publish messages to verify both bindings were recovered
        basicPublishVolatile("test1".getBytes(), "amq.topic", "topic1");
        basicPublishVolatile("test2".getBytes(), "amq.topic", "topic2");
        
        assertTrue(messagesReceivedLatch.await(10, TimeUnit.SECONDS));
    }
    
    @Test
    public void topologyRecoveryConsumerFailure() throws Exception {
        final String queue = "topology-recovery-retry-consumer-failure" + System.currentTimeMillis();
        channel.queueDeclare(queue, false, false, true, new HashMap<>());
        channel.queueBind(queue, "amq.topic", "topic1");
        channel.queueBind(queue, "amq.topic", "topic2");
        final CountDownLatch messagesReceivedLatch = new CountDownLatch(2);
        channel.basicConsume(queue, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
                messagesReceivedLatch.countDown();
            }
        });
        final CountDownLatch recoveryLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addRecoveryListener(new RecoveryListener() {
            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                // no-op
            }
            @Override
            public void handleRecovery(Recoverable recoverable) {
                recoveryLatch.countDown();
            }
        });
        
        // we want recovery to fail when recovering the consumer
        // give the recorded consumer a bad queue name so it fails
        final RecordedConsumer consumer = ((AutorecoveringConnection)connection).getRecordedConsumers().values().iterator().next();
        consumer.setQueue(UUID.randomUUID().toString());
        
        // use the backoffConsumer to know that it has failed
        // then delete the real queue & fix the recorded consumer
        // it should fail once more because queue is gone, and then succeed
        final CountDownLatch backoffLatch = new CountDownLatch(1);
        backoffConsumer = attempt -> {
            if (attempt == 1) {
                consumer.setQueue(queue);
                try {
                    Host.rabbitmqctl("delete_queue " + queue);
                    Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            backoffLatch.countDown();
        };
        
        // close connection
        Host.closeAllConnections();
        
        // assert backoff was called
        assertTrue(backoffLatch.await(90, TimeUnit.SECONDS));
        // wait for full recovery
        assertTrue(recoveryLatch.await(90, TimeUnit.SECONDS));
        
        // publish messages to verify both bindings & consumer were recovered
        basicPublishVolatile("test1".getBytes(), "amq.topic", "topic1");
        basicPublishVolatile("test2".getBytes(), "amq.topic", "topic2");
        
        assertTrue(messagesReceivedLatch.await(10, TimeUnit.SECONDS));
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setTopologyRecoveryRetryHandler(RETRY_ON_QUEUE_NOT_FOUND_RETRY_HANDLER
            .backoffPolicy(attempt -> backoffConsumer.accept(attempt)).build());
        connectionFactory.setNetworkRecoveryInterval(1000);
        return connectionFactory;
    }
}
