package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.impl.recovery.ConsumerRecoveryListener;
import com.rabbitmq.client.impl.recovery.QueueRecoveryListener;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConnectionRecovery extends BrokerTestCase {
    public static final long RECOVERY_INTERVAL = 2000;

    public void testConnectionRecovery() throws IOException, InterruptedException {
        assertTrue(connection.isOpen());
        closeAndWaitForRecovery();
        assertTrue(connection.isOpen());
    }

    public void testConnectionRecoveryWithServerRestart() throws IOException, InterruptedException {
        assertTrue(connection.isOpen());
        restartPrimaryAndWaitForRecovery();
        assertTrue(connection.isOpen());
    }

    public void testConnectionRecoveryWithMultipleAddresses()
            throws IOException, InterruptedException, TimeoutException {
        final Address[] addresses = {new Address("127.0.0.1"), new Address("127.0.0.1", 5672)};
        AutorecoveringConnection c = newRecoveringConnection(addresses);
        try {
            assertTrue(c.isOpen());
            closeAndWaitForRecovery(c);
            assertTrue(c.isOpen());
        } finally {
            c.abort();
        }

    }

    public void testConnectionRecoveryWithDisabledTopologyRecovery()
            throws IOException, InterruptedException, TimeoutException {
        AutorecoveringConnection c = newRecoveringConnection(true);
        Channel ch = c.createChannel();
        String q = "java-client.test.recovery.q2";
        ch.queueDeclare(q, false, true, false, null);
        ch.queueDeclarePassive(q);
        assertTrue(c.isOpen());
        try {
            CountDownLatch shutdownLatch = prepareForShutdown(c);
            CountDownLatch recoveryLatch = prepareForRecovery(c);
            Host.closeConnection(c);
            wait(shutdownLatch);
            wait(recoveryLatch);
            assertTrue(c.isOpen());
            ch.queueDeclarePassive(q);
            fail("expected passive declaration to throw");
        } catch (java.io.IOException e) {
            // expected
        } finally {
            c.abort();
        }
    }

    public void testShutdownHooksRecoveryOnConnection() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addShutdownListener(new ShutdownListener() {
            public void shutdownCompleted(ShutdownSignalException cause) {
                latch.countDown();
            }
        });
        assertTrue(connection.isOpen());
        closeAndWaitForRecovery();
        assertTrue(connection.isOpen());
        connection.close();
        wait(latch);
    }

    public void testShutdownHooksRecoveryOnChannel() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        channel.addShutdownListener(new ShutdownListener() {
            public void shutdownCompleted(ShutdownSignalException cause) {
                latch.countDown();
            }
        });
        assertTrue(connection.isOpen());
        closeAndWaitForRecovery();
        assertTrue(connection.isOpen());
        closeAndWaitForRecovery();
        assertTrue(connection.isOpen());
        connection.close();
        wait(latch);
    }

    public void testBlockedListenerRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addBlockedListener(new BlockedListener() {
            public void handleBlocked(String reason) throws IOException {
                latch.countDown();
            }

            public void handleUnblocked() throws IOException {
                latch.countDown();
            }
        });
        closeAndWaitForRecovery();
        block();
        channel.basicPublish("", "", null, "".getBytes());
        unblock();
        wait(latch);
    }

    public void testChannelRecovery() throws IOException, InterruptedException {
        Channel ch1 = connection.createChannel();
        Channel ch2 = connection.createChannel();

        assertTrue(ch1.isOpen());
        assertTrue(ch2.isOpen());
        closeAndWaitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
    }

    public void testReturnListenerRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        channel.addReturnListener(new ReturnListener() {
            public void handleReturn(int replyCode, String replyText, String exchange,
                                     String routingKey, AMQP.BasicProperties properties,
                                     byte[] body) throws IOException {
                latch.countDown();
            }
        });
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        channel.basicPublish("", "unknown", true, false, null, "mandatory1".getBytes());
        wait(latch);
    }

    public void testConfirmListenerRecovery() throws IOException, InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        channel.addConfirmListener(new ConfirmListener() {
            public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                latch.countDown();
            }

            public void handleNack(long deliveryTag, boolean multiple) throws IOException {
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

    public void testExchangeRecovery() throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        String x = "java-client.test.recovery.x1";
        declareExchange(ch, x);
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectExchangeRecovery(ch, x);
        ch.exchangeDelete(x);
    }

    public void testExchangeRecoveryWithNoWait() throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        String x = "java-client.test.recovery.x1-nowait";
        declareExchangeNoWait(ch, x);
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectExchangeRecovery(ch, x);
        ch.exchangeDelete(x);
    }

    public void testClientNamedQueueRecovery() throws IOException, InterruptedException, TimeoutException {
        testClientNamedQueueRecoveryWith("java-client.test.recovery.q1", false);
    }

    public void testClientNamedQueueRecoveryWithNoWait() throws IOException, InterruptedException, TimeoutException {
        testClientNamedQueueRecoveryWith("java-client.test.recovery.q1-nowait", true);
    }

    protected void testClientNamedQueueRecoveryWith(String q, boolean noWait) throws IOException, InterruptedException, TimeoutException {
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

    public void testClientNamedQueueBindingRecovery() throws IOException, InterruptedException, TimeoutException {
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
    public void testClientNamedTransientAutoDeleteQueueAndBindingRecovery() throws IOException, InterruptedException, TimeoutException {
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
        assertEquals(1, ok.getMessageCount());
        ch.queueDelete(q);
        ch.exchangeDelete(x);
    }

    // bug 26552
    public void testServerNamedTransientAutoDeleteQueueAndBindingRecovery() throws IOException, InterruptedException, TimeoutException {
        String x   = "tmp-fanout";
        Channel ch = connection.createChannel();
        ch.exchangeDelete(x);
        ch.exchangeDeclare(x, "fanout");
        String q = ch.queueDeclare("", false, false, true, null).getQueue();
        final AtomicReference<String> nameBefore = new AtomicReference<String>(q);
        final AtomicReference<String> nameAfter  = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addQueueRecoveryListener(new QueueRecoveryListener() {
            @Override
            public void queueRecovered(String oldName, String newName) {
                nameBefore.set(oldName);
                nameAfter.set(newName);
                listenerLatch.countDown();
            }
        });
        ch.queueBind(nameBefore.get(), x, "");
        restartPrimaryAndWaitForRecovery();
        expectChannelRecovery(ch);
        ch.confirmSelect();
        ch.exchangeDeclare(x, "fanout");
        ch.basicPublish(x, "", null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok = ch.queueDeclarePassive(nameAfter.get());
        assertEquals(1, ok.getMessageCount());
        ch.queueDelete(nameAfter.get());
        ch.exchangeDelete(x);
    }

    public void testDeclarationOfManyAutoDeleteQueuesWithTransientConsumer() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedQueues(connection, 0);
        for(int i = 0; i < 5000; i++) {
            String q = UUID.randomUUID().toString();
            ch.queueDeclare(q, false, false, true, null);
            QueueingConsumer dummy = new QueueingConsumer(ch);
            String tag = ch.basicConsume(q, true, dummy);
            ch.basicCancel(tag);
        }
        assertRecordedQueues(connection, 0);
        ch.close();
    }

    public void testDeclarationOfManyAutoDeleteExchangesWithTransientQueuesThatAreUnbound() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < 5000; i++) {
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

    public void testDeclarationOfManyAutoDeleteExchangesWithTransientQueuesThatAreDeleted() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < 5000; i++) {
            String x = UUID.randomUUID().toString();
            ch.exchangeDeclare(x, "fanout", false, true, null);
            String q = ch.queueDeclare().getQueue();
            ch.queueBind(q, x, "doesn't matter");
            ch.queueDelete(q);
        }
        assertRecordedExchanges(connection, 0);
        ch.close();
    }

    public void testDeclarationOfManyAutoDeleteExchangesWithTransientExchangesThatAreUnbound() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < 5000; i++) {
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

    public void testDeclarationOfManyAutoDeleteExchangesWithTransientExchangesThatAreDeleted() throws IOException, TimeoutException {
        Channel ch = connection.createChannel();
        assertRecordedExchanges(connection, 0);
        for(int i = 0; i < 5000; i++) {
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

    public void testServerNamedQueueRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x = "amq.fanout";
        channel.queueBind(q, x, "");

        final AtomicReference<String> nameBefore = new AtomicReference<String>();
        final AtomicReference<String> nameAfter  = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(1);
        ((AutorecoveringConnection)connection).addQueueRecoveryListener(new QueueRecoveryListener() {
            @Override
            public void queueRecovered(String oldName, String newName) {
                nameBefore.set(oldName);
                nameAfter.set(newName);
                listenerLatch.countDown();
            }
        });

        closeAndWaitForRecovery();
        wait(listenerLatch);
        expectChannelRecovery(channel);
        channel.basicPublish(x, "", null, "msg".getBytes());
        assertDelivered(q, 1);
        assertFalse(nameBefore.get().equals(nameAfter.get()));
        channel.queueDelete(q);
    }

    public void testExchangeToExchangeBindingRecovery() throws IOException, InterruptedException {
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

    public void testThatDeletedQueueBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
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

    public void testThatDeletedExchangeBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
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

    public void testThatDeletedExchangeDoesNotReappearOnRecover() throws IOException, InterruptedException {
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

    public void testThatDeletedQueueDoesNotReappearOnRecover() throws IOException, InterruptedException {
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

    public void testThatCancelledConsumerDoesNotReappearOnRecover() throws IOException, InterruptedException {
        String q = UUID.randomUUID().toString();
        channel.queueDeclare(q, false, false, false, null);
        String tag = channel.basicConsume(q, new DefaultConsumer(channel));
        assertConsumerCount(1, q);
        channel.basicCancel(tag);
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        assertConsumerCount(0, q);
    }

    public void testConsumerRecoveryWithManyConsumers() throws IOException, InterruptedException {
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();
        final int n = 1024;
        for (int i = 0; i < n; i++) {
            channel.basicConsume(q, new DefaultConsumer(channel));
        }
        final AtomicReference<String> tagA = new AtomicReference<String>();
        final AtomicReference<String> tagB = new AtomicReference<String>();
        final CountDownLatch listenerLatch = new CountDownLatch(n);
        ((AutorecoveringConnection)connection).addConsumerRecoveryListener(new ConsumerRecoveryListener() {
            @Override
            public void consumerRecovered(String oldConsumerTag, String newConsumerTag) {
                tagA.set(oldConsumerTag);
                tagB.set(newConsumerTag);
                listenerLatch.countDown();
            }
        });

        assertConsumerCount(n, q);
        closeAndWaitForRecovery();
        wait(listenerLatch);
        assertTrue(tagA.get().equals(tagB.get()));
        expectChannelRecovery(channel);
        assertConsumerCount(n, q);

    }

    public void testSubsequentRecoveriesWithClientNamedQueue() throws IOException, InterruptedException {
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();

        assertConsumerCount(0, q);
        channel.basicConsume(q, new DefaultConsumer(channel));

        for(int i = 0; i < 10; i++) {
            assertConsumerCount(1, q);
            closeAndWaitForRecovery();
        }

        channel.queueDelete(q);
    }

    public void testQueueRecoveryWithManyQueues() throws IOException, InterruptedException, TimeoutException {
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

    public void testChannelRecoveryCallback() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final RecoveryListener listener = new RecoveryListener() {
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
        };
        AutorecoveringChannel ch1 = (AutorecoveringChannel) connection.createChannel();
        ch1.addRecoveryListener(listener);
        AutorecoveringChannel ch2 = (AutorecoveringChannel) connection.createChannel();
        ch2.addRecoveryListener(listener);

        assertTrue(ch1.isOpen());
        assertTrue(ch2.isOpen());
        closeAndWaitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
        wait(latch);
    }

    public void testBasicAckAfterChannelRecovery() throws IOException, InterruptedException, TimeoutException {
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
        AutorecoveringConnection publishingConnection = newRecoveringConnection(false);
        Channel publishingChannel = publishingConnection.createChannel();
        for (int i = 0; i < n; i++) {
            publishingChannel.basicPublish("", q, null, "msg".getBytes());
        }
        wait(latch);
        publishingConnection.abort();
    }

    private void assertConsumerCount(int exp, String q) throws IOException {
        assertEquals(exp, channel.queueDeclarePassive(q).getConsumerCount());
    }

    private AMQP.Queue.DeclareOk declareClientNamedQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, false, null);
    }

    private AMQP.Queue.DeclareOk declareClientNamedAutoDeleteQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, true, null);
    }


    private void declareClientNamedQueueNoWait(Channel ch, String q) throws IOException {
        ch.queueDeclareNoWait(q, true, false, false, null);
    }

    private AMQP.Exchange.DeclareOk declareExchange(Channel ch, String x) throws IOException {
        return ch.exchangeDeclare(x, "fanout", false);
    }

    private void declareExchangeNoWait(Channel ch, String x) throws IOException {
        ch.exchangeDeclareNoWait(x, "fanout", false, false, false, null);
    }

    private void expectQueueRecovery(Channel ch, String q) throws IOException, InterruptedException, TimeoutException {
        ch.confirmSelect();
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = declareClientNamedQueue(ch, q);
        assertEquals(0, ok1.getMessageCount());
        ch.basicPublish("", q, null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok2 = declareClientNamedQueue(ch, q);
        assertEquals(1, ok2.getMessageCount());
    }

    private void expectAutoDeleteQueueAndBindingRecovery(Channel ch, String x, String q) throws IOException, InterruptedException,
                                                                                    TimeoutException {
        ch.confirmSelect();
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = declareClientNamedAutoDeleteQueue(ch, q);
        assertEquals(0, ok1.getMessageCount());
        ch.exchangeDeclare(x, "fanout");
        ch.basicPublish(x, "", null, "msg".getBytes());
        waitForConfirms(ch);
        AMQP.Queue.DeclareOk ok2 = declareClientNamedAutoDeleteQueue(ch, q);
        assertEquals(1, ok2.getMessageCount());
    }

    private void expectExchangeRecovery(Channel ch, String x) throws IOException, InterruptedException, TimeoutException {
        ch.confirmSelect();
        String q = ch.queueDeclare().getQueue();
        final String rk = "routing-key";
        ch.queueBind(q, x, rk);
        ch.basicPublish(x, rk, null, "msg".getBytes());
        waitForConfirms(ch);
        ch.exchangeDeclarePassive(x);
    }

    private CountDownLatch prepareForRecovery(Connection conn) {
        final CountDownLatch latch = new CountDownLatch(1);
        ((AutorecoveringConnection)conn).addRecoveryListener(new RecoveryListener() {
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
        });
        return latch;
    }

    private CountDownLatch prepareForShutdown(Connection conn) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        conn.addShutdownListener(new ShutdownListener() {
            public void shutdownCompleted(ShutdownSignalException cause) {
                latch.countDown();
            }
        });
        return latch;
    }

    private void closeAndWaitForRecovery() throws IOException, InterruptedException {
        closeAndWaitForRecovery((AutorecoveringConnection)this.connection);
    }

    private void closeAndWaitForRecovery(AutorecoveringConnection connection) throws IOException, InterruptedException {
        CountDownLatch latch = prepareForRecovery(connection);
        Host.closeConnection(connection);
        wait(latch);
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

    private void expectChannelRecovery(Channel ch) throws InterruptedException {
        assertTrue(ch.isOpen());
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        return buildConnectionFactoryWithRecoveryEnabled(false);
    }

    private AutorecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection();
    }

    private AutorecoveringConnection newRecoveringConnection(Address[] addresses)
            throws IOException, TimeoutException {
        return newRecoveringConnection(false, addresses);
    }

    private AutorecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery, Address[] addresses)
            throws IOException, TimeoutException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection(addresses);
    }

    private ConnectionFactory buildConnectionFactoryWithRecoveryEnabled(boolean disableTopologyRecovery) {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        cf.setAutomaticRecoveryEnabled(true);
        if (disableTopologyRecovery) {
            cf.setTopologyRecoveryEnabled(false);
        }
        return cf;
    }

    private static void wait(CountDownLatch latch) throws InterruptedException {
        // Very very generous amount of time to wait, just make sure we never
        // hang forever
        assertTrue(latch.await(1800, TimeUnit.SECONDS));
    }

    private void waitForConfirms(Channel ch) throws InterruptedException, TimeoutException {
        ch.waitForConfirms(30 * 60 * 1000);
    }

    protected void assertRecordedQueues(Connection conn, int size) {
        assertEquals(size, ((AutorecoveringConnection)conn).getRecordedQueues().size());
    }

    protected void assertRecordedExchanges(Connection conn, int size) {
        assertEquals(size, ((AutorecoveringConnection)conn).getRecordedExchanges().size());
    }
}
