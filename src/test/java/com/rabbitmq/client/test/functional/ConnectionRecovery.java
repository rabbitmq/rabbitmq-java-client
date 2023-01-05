// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.client.impl.recovery.*;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import java.lang.reflect.Field;
import java.util.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.rabbitmq.client.test.TestUtils.prepareForRecovery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ThrowFromFinallyBlock")
public class ConnectionRecovery extends BrokerTestCase {
    private static final long RECOVERY_INTERVAL = 2000;

    private static final int MANY_DECLARATIONS_LOOP_COUNT = 500;

    @Test public void connectionRecovery() throws IOException, InterruptedException {
        assertThat(connection.isOpen()).isTrue();
        closeAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
    }

    @Test public void namedConnectionRecovery()
            throws IOException, InterruptedException, TimeoutException  {
        String connectionName = "custom-name";
        RecoverableConnection c = newRecoveringConnection(connectionName);
        try {
            assertThat(c.isOpen()).isTrue();
            assertThat(connectionName).isEqualTo(c.getClientProvidedName());
            TestUtils.closeAndWaitForRecovery(c);
            assertThat(c.isOpen()).isTrue();
            assertThat(connectionName).isEqualTo(c.getClientProvidedName());
        } finally {
            c.abort();
        }
    }

    @Test public void connectionRecoveryWithServerRestart() throws IOException, InterruptedException {
        assertThat(connection.isOpen()).isTrue();
        restartPrimaryAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
    }

    @Test public void connectionRecoveryWithArrayOfAddresses()
            throws IOException, InterruptedException, TimeoutException {
        final Address[] addresses = {new Address("127.0.0.1"), new Address("127.0.0.1", 5672)};
        RecoverableConnection c = newRecoveringConnection(addresses);
        try {
            assertThat(c.isOpen()).isTrue();
            TestUtils.closeAndWaitForRecovery(c);
            assertThat(c.isOpen()).isTrue();
        } finally {
            c.abort();
        }

    }

    @Test public void connectionRecoveryWithListOfAddresses()
            throws IOException, InterruptedException, TimeoutException {

        final List<Address> addresses = Arrays.asList(new Address("127.0.0.1"), new Address("127.0.0.1", 5672));

        RecoverableConnection c = newRecoveringConnection(addresses);
        try {
            assertThat(c.isOpen()).isTrue();
            TestUtils.closeAndWaitForRecovery(c);
            assertThat(c.isOpen()).isTrue();
        } finally {
            c.abort();
        }
    }

    @Test public void connectionRecoveryWithDisabledTopologyRecovery()
            throws IOException, InterruptedException, TimeoutException {
        RecoverableConnection c = newRecoveringConnection(true);
        Channel ch = c.createChannel();
        String q = "java-client.test.recovery.q2";
        ch.queueDeclare(q, false, true, false, null);
        ch.queueDeclarePassive(q);
        assertThat(c.isOpen()).isTrue();
        try {
            CountDownLatch shutdownLatch = prepareForShutdown(c);
            CountDownLatch recoveryLatch = prepareForRecovery(c);
            Host.closeConnection((NetworkConnection) c);
            wait(shutdownLatch);
            wait(recoveryLatch);
            assertThat(c.isOpen()).isTrue();
            ch.queueDeclarePassive(q);
            fail("expected passive declaration to throw");
        } catch (java.io.IOException e) {
            // expected
        } finally {
            c.abort();
        }
    }
    
    // See https://github.com/rabbitmq/rabbitmq-java-client/pull/350 .
    // We want to request fresh creds when recovering.
    @Test public void connectionRecoveryRequestsCredentialsAgain() throws Exception {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(false);
        final String username = cf.getUsername();
        final String password = cf.getPassword();
        final AtomicInteger usernameRequested = new AtomicInteger(0);
        final AtomicInteger passwordRequested = new AtomicInteger(0);
        cf.setCredentialsProvider(new CredentialsProvider() {
            
            @Override
            public String getUsername() {
                usernameRequested.incrementAndGet();
                return username;
            }
            
            @Override
            public String getPassword() {
                passwordRequested.incrementAndGet();
                return password;
            }
        });
        RecoverableConnection c = (RecoverableConnection) cf.newConnection(UUID.randomUUID().toString());
        try {
            assertThat(c.isOpen()).isTrue();
            assertThat(usernameRequested.get()).isEqualTo(1);
            assertThat(passwordRequested.get()).isEqualTo(1);

            TestUtils.closeAndWaitForRecovery(c);
            assertThat(c.isOpen()).isTrue();
            // username is requested in AMQConnection#toString, so it can be accessed at any time
            assertThat(usernameRequested.get()).isGreaterThanOrEqualTo(2);
            assertThat(passwordRequested.get()).isEqualTo(2);
        } finally {
            c.abort();
        }
    }

    // see https://github.com/rabbitmq/rabbitmq-java-client/issues/135
    @Test public void thatShutdownHooksOnConnectionFireBeforeRecoveryStarts() throws IOException, InterruptedException {
        final List<String> events = new CopyOnWriteArrayList<String>();
        final CountDownLatch latch = new CountDownLatch(3); // one when started, another when complete
        connection.addShutdownListener(cause -> events.add("shutdown hook 1"));
        connection.addShutdownListener(cause -> events.add("shutdown hook 2"));
        // note: we do not want to expose RecoveryCanBeginListener so this
        // test does not use it
        final CountDownLatch recoveryCanBeginLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).getDelegate().addRecoveryCanBeginListener(new RecoveryCanBeginListener() {
            @Override
            public void recoveryCanBegin(ShutdownSignalException cause) {
                events.add("recovery start hook 1");
                recoveryCanBeginLatch.countDown();
            }
        });
        ((RecoverableConnection)connection).addRecoveryListener(new RecoveryListener() {
            @Override
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                latch.countDown();
            }
            @Override
            public void handleTopologyRecoveryStarted(Recoverable recoverable) {
                latch.countDown();
            }
        });
        assertThat(connection.isOpen()).isTrue();
        closeAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
        assertThat(events).element(0).isEqualTo("shutdown hook 1");
        assertThat(events).element(1).isEqualTo("shutdown hook 2");
        recoveryCanBeginLatch.await(5, TimeUnit.SECONDS);
        assertThat(events).element(2).isEqualTo("recovery start hook 1");
        connection.close();
        wait(latch);
    }

    @Test public void shutdownHooksRecoveryOnConnection() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addShutdownListener(cause -> latch.countDown());
        assertThat(connection.isOpen()).isTrue();
        closeAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
        connection.close();
        wait(latch);
    }

    @Test public void shutdownHooksRecoveryOnChannel() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        channel.addShutdownListener(cause -> latch.countDown());
        assertThat(connection.isOpen()).isTrue();
        closeAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
        closeAndWaitForRecovery();
        assertThat(connection.isOpen()).isTrue();
        connection.close();
        wait(latch);
    }

    @Test public void blockedListenerRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addBlockedListener(new BlockedListener() {
            @Override
            public void handleBlocked(String reason) {
                latch.countDown();
            }

            @Override
            public void handleUnblocked() {
                latch.countDown();
            }
        });
        closeAndWaitForRecovery();
        block();
        channel.basicPublish("", "", null, "".getBytes());
        unblock();
        wait(latch);
    }

    @Test public void channelRecovery() throws IOException, InterruptedException {
        Channel ch1 = connection.createChannel();
        Channel ch2 = connection.createChannel();

        assertThat(ch1.isOpen()).isTrue();
        assertThat(ch2.isOpen()).isTrue();
        closeAndWaitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
    }

    @Test public void channelRecoveryWithUserProvidedChannelIDs() throws IOException, InterruptedException {
        int n1 = 11;
        Channel ch1 = connection.createChannel(n1);
        int n2 = 22;
        Channel ch2 = connection.createChannel(n2);

        assertThat(ch1.isOpen()).isTrue();
        assertThat(ch2.isOpen()).isTrue();
        closeAndWaitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);

        assertThat(ch1.getChannelNumber()).isEqualTo(n1);
        assertThat(ch2.getChannelNumber()).isEqualTo(n2);
    }

    @Test public void returnListenerRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        channel.addReturnListener(
            (replyCode, replyText, exchange, routingKey, properties, body) -> latch.countDown());
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        channel.basicPublish("", "unknown", true, false, null, "mandatory1".getBytes());
        wait(latch);
    }

    @Test public void confirmListenerRecovery() throws IOException, InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        channel.addConfirmListener(new ConfirmListener() {
            @Override
            public void handleAck(long deliveryTag, boolean multiple) {
                latch.countDown();
            }

            @Override
            public void handleNack(long deliveryTag, boolean multiple) {
                latch.countDown();
            }
        });
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        channel.confirmSelect();
        basicPublishVolatile(q);
        waitForConfirms(channel);
        wait(latch);
    }

    @Test public void exchangeRecovery() throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        String x = "java-client.test.recovery.x1";
        declareExchange(ch, x);
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectExchangeRecovery(ch, x);
        ch.exchangeDelete(x);
    }

    @Test public void exchangeRecoveryWithNoWait() throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        String x = "java-client.test.recovery.x1-nowait";
        declareExchangeNoWait(ch, x);
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectExchangeRecovery(ch, x);
        ch.exchangeDelete(x);
    }

    @Test public void clientNamedQueueRecovery() throws IOException, InterruptedException, TimeoutException {
        testClientNamedQueueRecoveryWith("java-client.test.recovery.q1", false);
    }

    @Test public void clientNamedQueueRecoveryWithNoWait() throws IOException, InterruptedException, TimeoutException {
        testClientNamedQueueRecoveryWith("java-client.test.recovery.q1-nowait", true);
    }

    private void testClientNamedQueueRecoveryWith(String q, boolean noWait) throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        if(noWait) {
            declareClientNamedQueueNoWait(ch, q);
        } else {
            declareClientNamedQueue(ch, q);
        }
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectQueueRecovery(ch, q);
        ch.queueDelete(q);
    }

    @Test public void clientNamedQueueBindingRecovery() throws IOException, InterruptedException, TimeoutException {
        String q   = "java-client.test.recovery.q2";
        String x   = "tmp-fanout";
        Channel ch = connection.createChannel();
        ch.queueDelete(q);
        ch.exchangeDelete(x);
        ch.exchangeDeclare(x, "fanout");
        declareClientNamedAutoDeleteQueue(ch, q);
        ch.queueBind(q, x, "");
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectAutoDeleteQueueAndBindingRecovery(ch, x, q);
        ch.queueDelete(q);
        ch.exchangeDelete(x);
    }

    // bug 26552
    @Test public void clientNamedTransientAutoDeleteQueueAndBindingRecovery() throws IOException, InterruptedException, TimeoutException {
        String q   = UUID.randomUUID().toString();
        String x   = "tmp-fanout";
        Channel ch = connection.createChannel();
        ch.queueDelete(q);
        ch.exchangeDelete(x);
        ch.exchangeDeclare(x, "fanout");
        ch.queueDeclare(q, false, false, true, null);
        ch.queueBind(q, x, "");
        restartPrimaryAndWaitForRecovery();
        expectChannelRecovery(ch);
        ch.confirmSelect();
        ch.queuePurge(q);
        ch.exchangeDeclare(x, "fanout");
        ch.basicPublish(x, "", null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok = ch.queueDeclare(q, false, false, true, null);
        assertThat(ok.getMessageCount()).isEqualTo(1);
        ch.queueDelete(q);
        ch.exchangeDelete(x);
    }

    // bug 26552
    @Test public void serverNamedTransientAutoDeleteQueueAndBindingRecovery() throws IOException, InterruptedException, TimeoutException {
        String x   = "tmp-fanout";
        Channel ch = connection.createChannel();
        ch.exchangeDelete(x);
        ch.exchangeDeclare(x, "fanout");
        String q = ch.queueDeclare("", false, false, true, null).getQueue();
        final AtomicReference<String> nameBefore = new AtomicReference<String>(q);
        final AtomicReference<String> nameAfter  = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addQueueRecoveryListener((oldName, newName) -> {
            nameBefore.set(oldName);
            nameAfter.set(newName);
            listenerLatch.countDown();
        });
        ch.queueBind(nameBefore.get(), x, "");
        restartPrimaryAndWaitForRecovery();
        expectChannelRecovery(ch);
        ch.confirmSelect();
        ch.exchangeDeclare(x, "fanout");
        ch.basicPublish(x, "", null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok = ch.queueDeclarePassive(nameAfter.get());
        assertThat(ok.getMessageCount()).isEqualTo(1);
        ch.queueDelete(nameAfter.get());
        ch.exchangeDelete(x);
    }

    @Test public void declarationOfManyAutoDeleteQueuesWithTransientConsumer() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedQueues(connection, 0);
        for(int i = 0; i < MANY_DECLARATIONS_LOOP_COUNT; i++) {
            String q = UUID.randomUUID().toString();
            ch.queueDeclare(q, false, false, true, null);
            DefaultConsumer dummy = new DefaultConsumer(ch);
            String tag = ch.basicConsume(q, true, dummy);
            ch.basicCancel(tag);
        }
        assertRecordedQueues(connection, 0);
        ch.close();
    }

    @Test public void declarationOfManyAutoDeleteExchangesWithTransientQueuesThatAreUnbound() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < MANY_DECLARATIONS_LOOP_COUNT; i++) {
            String x = UUID.randomUUID().toString();
            ch.exchangeDeclare(x, "fanout", false, true, null);
            String q = ch.queueDeclare().getQueue();
            final String rk = "doesn't matter";
            ch.queueBind(q, x, rk);
            ch.queueUnbind(q, x, rk);
            ch.queueDelete(q);
        }
        assertRecordedExchanges(connection, 0);
        ch.close();
    }

    @Test public void declarationOfManyAutoDeleteExchangesWithTransientQueuesThatAreDeleted() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < MANY_DECLARATIONS_LOOP_COUNT; i++) {
            String x = UUID.randomUUID().toString();
            ch.exchangeDeclare(x, "fanout", false, true, null);
            String q = ch.queueDeclare().getQueue();
            ch.queueBind(q, x, "doesn't matter");
            ch.queueDelete(q);
        }
        assertRecordedExchanges(connection, 0);
        ch.close();
    }

    @Test public void declarationOfManyAutoDeleteExchangesWithTransientExchangesThatAreUnbound() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < MANY_DECLARATIONS_LOOP_COUNT; i++) {
            String src = "src-" + UUID.randomUUID().toString();
            String dest = "dest-" + UUID.randomUUID().toString();
            ch.exchangeDeclare(src, "fanout", false, true, null);
            ch.exchangeDeclare(dest, "fanout", false, true, null);
            final String rk = "doesn't matter";
            ch.exchangeBind(dest, src, rk);
            ch.exchangeUnbind(dest, src, rk);
            ch.exchangeDelete(dest);
        }
        assertRecordedExchanges(connection, 0);
        ch.close();
    }

    @Test public void declarationOfManyAutoDeleteExchangesWithTransientExchangesThatAreDeleted() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < MANY_DECLARATIONS_LOOP_COUNT; i++) {
            String src = "src-" + UUID.randomUUID().toString();
            String dest = "dest-" + UUID.randomUUID().toString();
            ch.exchangeDeclare(src, "fanout", false, true, null);
            ch.exchangeDeclare(dest, "fanout", false, true, null);
            ch.exchangeBind(dest, src, "doesn't matter");
            ch.exchangeDelete(dest);
        }
        assertRecordedExchanges(connection, 0);
        ch.close();
    }

    @Test public void serverNamedQueueRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x = "amq.fanout";
        channel.queueBind(q, x, "");

        final AtomicReference<String> nameBefore = new AtomicReference<String>();
        final AtomicReference<String> nameAfter  = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addQueueRecoveryListener((oldName, newName) -> {
            nameBefore.set(oldName);
            nameAfter.set(newName);
            listenerLatch.countDown();
        });

        closeAndWaitForRecovery();
        wait(listenerLatch);
        expectChannelRecovery(channel);
        channel.basicPublish(x, "", null, "msg".getBytes());
        assertDelivered(q, 1);
        assertThat(nameBefore).doesNotHaveValue(nameAfter.get());
        channel.queueDelete(q);
    }

    @Test public void exchangeToExchangeBindingRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");

        try {
            closeAndWaitForRecovery();
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertDelivered(q, 1);
        } finally {
            channel.exchangeDelete(x2);
            channel.queueDelete(q);
        }
    }

    @Test public void thatDeletedQueueBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");
        channel.queueUnbind(q, x1, "");

        try {
            closeAndWaitForRecovery();
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertDelivered(q, 0);
        } finally {
            channel.exchangeDelete(x2);
            channel.queueDelete(q);
        }
    }

    @Test public void thatDeletedExchangeBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");
        channel.exchangeUnbind(x1, x2, "");

        try {
            closeAndWaitForRecovery();
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertDelivered(q, 0);
        } finally {
            channel.exchangeDelete(x2);
            channel.queueDelete(q);
        }
    }

    @Test public void thatDeletedExchangeDoesNotReappearOnRecover() throws IOException, InterruptedException {
        String x = generateExchangeName();
        channel.exchangeDeclare(x, "fanout");
        channel.exchangeDelete(x);
        try {
            closeAndWaitForRecovery();
            expectChannelRecovery(channel);
            channel.exchangeDeclarePassive(x);
            fail("Expected passive declare to fail");
        } catch (IOException ioe) {
            // expected
        }
    }

    @Test public void thatDeletedQueueDoesNotReappearOnRecover() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        channel.queueDelete(q);
        try {
            closeAndWaitForRecovery();
            expectChannelRecovery(channel);
            channel.queueDeclarePassive(q);
            fail("Expected passive declare to fail");
        } catch (IOException ioe) {
            // expected
        }
    }
    
    @Test public void thatExcludedQueueDoesNotReappearOnRecover() throws IOException, InterruptedException {
        final String q = "java-client.test.recovery.excludedQueue1";
        channel.queueDeclare(q, true, false, false, null);
        // now delete it using the delegate so AutorecoveringConnection and AutorecoveringChannel are not aware of it
        ((AutorecoveringChannel)channel).getDelegate().queueDelete(q);
        assertThat(((AutorecoveringConnection)connection).getRecordedQueues().get(q)).isNotNull();
        // exclude the queue from recovery
        ((AutorecoveringConnection)connection).excludeQueueFromRecovery(q, true);
        // verify its not there
        assertThat(((AutorecoveringConnection)connection).getRecordedQueues().get(q)).isNull();
        // reconnect
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        // verify queue was not recreated
        try {
            channel.queueDeclarePassive(q);
            fail("Expected passive declare to fail");
        } catch (IOException ioe) {
            // expected
        }
    }

    @Test public void thatCancelledConsumerDoesNotReappearOnRecover() throws IOException, InterruptedException {
        String q = UUID.randomUUID().toString();
        channel.queueDeclare(q, false, false, false, null);
        String tag = channel.basicConsume(q, new DefaultConsumer(channel));
        assertConsumerCount(1, q);
        channel.basicCancel(tag);
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        assertConsumerCount(0, q);
    }

    @Test public void consumerRecoveryWithManyConsumers() throws IOException, InterruptedException {
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();
        final int n = 1024;
        for (int i = 0; i < n; i++) {
            channel.basicConsume(q, new DefaultConsumer(channel));
        }
        final AtomicReference<String> tagA = new AtomicReference<String>();
        final AtomicReference<String> tagB = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(n);
        ((AutorecoveringConnection)connection).addConsumerRecoveryListener(
            (oldConsumerTag, newConsumerTag) -> {
                tagA.set(oldConsumerTag);
                tagB.set(newConsumerTag);
                listenerLatch.countDown();
            });

        assertConsumerCount(n, q);
        closeAndWaitForRecovery();
        wait(listenerLatch);
        assertThat(tagA.get().equals(tagB.get())).isTrue();
        expectChannelRecovery(channel);
        assertConsumerCount(n, q);

    }

    @Test public void subsequentRecoveriesWithClientNamedQueue() throws IOException, InterruptedException {
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();

        assertConsumerCount(0, q);
        channel.basicConsume(q, new DefaultConsumer(channel));

        for(int i = 0; i < 10; i++) {
            assertConsumerCount(1, q);
            closeAndWaitForRecovery();
        }

        channel.queueDelete(q);
    }

    @Test public void queueRecoveryWithManyQueues() throws IOException, InterruptedException, TimeoutException {
        List<String> qs = new ArrayList<String>();
        final int n = 1024;
        for (int i = 0; i < n; i++) {
            qs.add(channel.queueDeclare(UUID.randomUUID().toString(), true, false, false, null).getQueue());
        }
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        for(String q : qs) {
            expectQueueRecovery(channel, q);
            channel.queueDelete(q);
        }
    }

    @Test public void channelRecoveryCallback() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final CountDownLatch startLatch = new CountDownLatch(2);
        final RecoveryListener listener = new RecoveryListener() {
            @Override
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
            @Override
            public void handleRecoveryStarted(Recoverable recoverable) {
                startLatch.countDown();
            }
        };
        RecoverableChannel ch1 = (RecoverableChannel) connection.createChannel();
        ch1.addRecoveryListener(listener);
        RecoverableChannel ch2 = (RecoverableChannel) connection.createChannel();
        ch2.addRecoveryListener(listener);

        assertThat(ch1.isOpen()).isTrue();
        assertThat(ch2.isOpen()).isTrue();
        closeAndWaitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
        wait(latch);
        wait(startLatch);
    }

    @Test public void basicAckAfterChannelRecovery() throws IOException, InterruptedException, TimeoutException {
        final AtomicInteger consumed = new AtomicInteger(0);
        int n = 5;
        final CountDownLatch latch = new CountDownLatch(n);
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) throws IOException {
                try {
                    if (consumed.intValue() > 0 && consumed.intValue() % 4 == 0) {
                        CountDownLatch recoveryLatch = prepareForRecovery(connection);
                        Host.closeConnection((AutorecoveringConnection)connection);
                        ConnectionRecovery.wait(recoveryLatch);
                    }
                    channel.basicAck(envelope.getDeliveryTag(), false);
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    consumed.incrementAndGet();
                    latch.countDown();
                }
            }
        };

        String q = channel.queueDeclare().getQueue();
        channel.basicConsume(q, consumer);
        RecoverableConnection publishingConnection = newRecoveringConnection(false);
        Channel publishingChannel = publishingConnection.createChannel();
        for (int i = 0; i < n; i++) {
            publishingChannel.basicPublish("", q, null, "msg".getBytes());
        }
        wait(latch);
        publishingConnection.abort();
    }

    @Test public void consumersAreRemovedFromConnectionWhenChannelIsClosed() throws Exception {
        RecoverableConnection connection = newRecoveringConnection(true);
        try {
            Field consumersField = AutorecoveringConnection.class.getDeclaredField("consumers");
            consumersField.setAccessible(true);
            Map<?, ?> connectionConsumers = (Map<?, ?>) consumersField.get(connection);

            Channel channel1 = connection.createChannel();
            Channel channel2 = connection.createChannel();

            assertThat(connectionConsumers).isEmpty();

            String queue = channel1.queueDeclare().getQueue();

            channel1.basicConsume(queue, true, new HashMap<>(), new DefaultConsumer(channel1));
            assertThat(connectionConsumers).hasSize(1);
            channel1.basicConsume(queue, true, new HashMap<>(), new DefaultConsumer(channel1));
            assertThat(connectionConsumers).hasSize(2);

            channel2.basicConsume(queue, true, new HashMap<>(), new DefaultConsumer(channel2));
            assertThat(connectionConsumers).hasSize(3);

            channel1.close();
            assertThat(connectionConsumers).hasSize(3 - 2);

            channel2.close();
            assertThat(connectionConsumers).isEmpty();
        } finally {
            connection.abort();
        }
    }

    @Test public void recoveryWithExponentialBackoffDelayHandler() throws Exception {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setRecoveryDelayHandler(new RecoveryDelayHandler.ExponentialBackoffDelayHandler());
        Connection testConnection = connectionFactory.newConnection(UUID.randomUUID().toString());
        try {
            assertThat(testConnection.isOpen()).isTrue();
            TestUtils.closeAndWaitForRecovery((RecoverableConnection) testConnection);
            assertThat(testConnection.isOpen()).isTrue();
        } finally {
            connection.close();
        }
    }
    
    @Test public void recoveryWithMultipleThreads() throws Exception {
        // test with 8 recovery threads
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        ConnectionFactory connectionFactory = buildConnectionFactoryWithRecoveryEnabled(false);
        assertThat(connectionFactory.getTopologyRecoveryExecutor()).isNull();
        connectionFactory.setTopologyRecoveryExecutor(executor);
        assertThat(connectionFactory.getTopologyRecoveryExecutor()).isEqualTo(executor);
        RecoverableConnection testConnection = (RecoverableConnection) connectionFactory.newConnection(
            UUID.randomUUID().toString()
        );
        try {
            final List<Channel> channels = new ArrayList<Channel>();
            final List<String> exchanges = new ArrayList<String>();
            final List<String> queues = new ArrayList<String>();
            // create 16 channels
            final int channelCount = 16;
            final int queuesPerChannel = 20;
            final CountDownLatch latch = new CountDownLatch(channelCount * queuesPerChannel);
            for (int i=0; i < channelCount; i++) {
                final Channel testChannel = testConnection.createChannel();
                channels.add(testChannel);
                String x = "tmp-x-topic-" + i;
                exchanges.add(x);
                testChannel.exchangeDeclare(x, "topic");
                // create 20 queues and bindings per channel
                for (int j=0; j < queuesPerChannel; j++) {
                    String q = "tmp-q-" + i + "-" + j;
                    queues.add(q);
                    testChannel.queueDeclare(q, false, false, true, null);
                    testChannel.queueBind(q, x, "tmp-key-" + i + "-" + j);
                    testChannel.basicConsume(q, new DefaultConsumer(testChannel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
                                throws IOException {
                            testChannel.basicAck(envelope.getDeliveryTag(), false);
                            latch.countDown();
                        }
                    }); 
                }
            }
            // now do recovery
            TestUtils.closeAndWaitForRecovery(testConnection);
            
            // verify channels & topology recovered by publishing a message to each
            for (int i=0; i < channelCount; i++) {
                Channel ch = channels.get(i);
                expectChannelRecovery(ch);
                // publish message to each queue/consumer
                for (int j=0; j < queuesPerChannel; j++) {
                    ch.basicPublish("tmp-x-topic-" + i, "tmp-key-" + i + "-" + j, null, "msg".getBytes());
                }
            }
            // verify all queues/consumers got it
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            
            // cleanup
            Channel cleanupChannel = testConnection.createChannel();
            for (String q : queues)
                cleanupChannel.queueDelete(q);
            for (String x : exchanges)
                cleanupChannel.exchangeDelete(x);
        } finally {
            testConnection.close();
        }
    }

    private void assertConsumerCount(int exp, String q) throws IOException {
        assertThat(channel.queueDeclarePassive(q).getConsumerCount()).isEqualTo(exp);
    }

    private static AMQP.Queue.DeclareOk declareClientNamedQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, false, null);
    }

    private static AMQP.Queue.DeclareOk declareClientNamedAutoDeleteQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, true, null);
    }


    private static void declareClientNamedQueueNoWait(Channel ch, String q) throws IOException {
        ch.queueDeclareNoWait(q, true, false, false, null);
    }

    private static AMQP.Exchange.DeclareOk declareExchange(Channel ch, String x) throws IOException {
        return ch.exchangeDeclare(x, "fanout", false);
    }

    private static void declareExchangeNoWait(Channel ch, String x) throws IOException {
        ch.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
    }

    private static void expectQueueRecovery(Channel ch, String q) throws IOException, InterruptedException, TimeoutException {
        ch.confirmSelect();
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = declareClientNamedQueue(ch, q);
        assertThat(ok1.getMessageCount()).isEqualTo(0);
        ch.basicPublish("", q, null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok2 = declareClientNamedQueue(ch, q);
        assertThat(ok2.getMessageCount()).isEqualTo(1);
    }

    private static void expectAutoDeleteQueueAndBindingRecovery(Channel ch, String x, String q) throws IOException, InterruptedException,
                                                                                    TimeoutException {
        ch.confirmSelect();
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = declareClientNamedAutoDeleteQueue(ch, q);
        assertThat(ok1.getMessageCount()).isEqualTo(0);
        ch.exchangeDeclare(x, "fanout");
        ch.basicPublish(x, "", null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok2 = declareClientNamedAutoDeleteQueue(ch, q);
        assertThat(ok2.getMessageCount()).isEqualTo(1);
    }

    private static void expectExchangeRecovery(Channel ch, String x) throws IOException, InterruptedException, TimeoutException {
        ch.confirmSelect();
        String q = ch.queueDeclare().getQueue();
        final String rk = "routing-key";
        ch.queueBind(q, x, rk);
        ch.basicPublish(x, rk, null, "msg".getBytes());
        waitForConfirms(ch);
        ch.exchangeDeclarePassive(x);
    }

    private static CountDownLatch prepareForShutdown(Connection conn) {
        final CountDownLatch latch = new CountDownLatch(1);
        conn.addShutdownListener(cause -> latch.countDown());
        return latch;
    }

    private void closeAndWaitForRecovery() throws IOException, InterruptedException {
        TestUtils.closeAndWaitForRecovery((AutorecoveringConnection)this.connection);
    }

    private void restartPrimaryAndWaitForRecovery() throws IOException, InterruptedException {
        restartPrimaryAndWaitForRecovery(this.connection);
    }

    private void restartPrimaryAndWaitForRecovery(Connection connection) throws IOException, InterruptedException {
        CountDownLatch latch = prepareForRecovery(connection);
        // restart without tearing down and setting up
        // new connection and channel
        bareRestart();
        wait(latch);
    }

    private static void expectChannelRecovery(Channel ch) {
        assertThat(ch.isOpen()).isTrue();
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        return buildConnectionFactoryWithRecoveryEnabled(false);
    }

    private static RecoverableConnection newRecoveringConnection(boolean disableTopologyRecovery)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection(UUID.randomUUID().toString());
    }

    private static RecoverableConnection newRecoveringConnection(Address[] addresses)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(false);
        // specifically use the Address[] overload
        return (AutorecoveringConnection) cf.newConnection(addresses, UUID.randomUUID().toString());
    }

    private static RecoverableConnection newRecoveringConnection(boolean disableTopologyRecovery, List<Address> addresses)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection(addresses, UUID.randomUUID().toString());
    }

    private static RecoverableConnection newRecoveringConnection(List<Address> addresses)
            throws IOException, TimeoutException {
        return newRecoveringConnection(false, addresses);
    }

    private static RecoverableConnection newRecoveringConnection(boolean disableTopologyRecovery, String connectionName)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (RecoverableConnection) cf.newConnection(connectionName);
    }

    private static RecoverableConnection newRecoveringConnection(String connectionName)
            throws IOException, TimeoutException {
        return newRecoveringConnection(false, connectionName);
    }
    
    private static ConnectionFactory buildConnectionFactoryWithRecoveryEnabled(boolean disableTopologyRecovery) {
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        cf.setAutomaticRecoveryEnabled(true);
        if (disableTopologyRecovery) {
            cf.setTopologyRecoveryEnabled(false);
        }
        return cf;
    }

    private static void wait(CountDownLatch latch) throws InterruptedException {
        // we want to wait for recovery to complete for a reasonable amount of time
        // but still make recovery failures easy to notice in development environments
        assertThat(latch.await(90, TimeUnit.SECONDS)).isTrue();
    }

    private static void waitForConfirms(Channel ch) throws InterruptedException, TimeoutException {
        ch.waitForConfirms(30 * 60 * 1000);
    }

    private static void assertRecordedQueues(Connection conn, int size) {
        assertThat(((AutorecoveringConnection)conn).getRecordedQueues()).hasSize(size);
    }

    private static void assertRecordedExchanges(Connection conn, int size) {
        assertThat(((AutorecoveringConnection)conn).getRecordedExchanges()).hasSize(size);
    }
}
