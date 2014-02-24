package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionRecovery extends BrokerTestCase {
    public static final int RECOVERY_INTERVAL = 2000;

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

    public void testConnectionRecoveryWithMultipleAddresses() throws IOException, InterruptedException {
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

    public void testConnectionRecoveryWithDisabledTopologyRecovery() throws IOException, InterruptedException {
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

    public void testShutdownHooksRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addShutdownListener(new ShutdownListener() {
            @Override
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

    public void testBlockedListenerRecovery() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        connection.addBlockedListener(new BlockedListener() {
            @Override
            public void handleBlocked(String reason) throws IOException {
                latch.countDown();
            }

            @Override
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
            @Override
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
        int n = 3;
        final CountDownLatch latch = new CountDownLatch(n);
        channel.addConfirmListener(new ConfirmListener() {
            @Override
            public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                latch.countDown();
            }

            @Override
            public void handleNack(long deliveryTag, boolean multiple) throws IOException {
                latch.countDown();
            }
        });
        String q = channel.queueDeclare(UUID.randomUUID().toString(), false, false, false, null).getQueue();
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        channel.confirmSelect();
        for (int i = 0; i < n * 20; i++) {
            channel.basicPublish("", q, true, false, null, "mandatory1".getBytes());
        }
        waitForConfirms(channel);
        wait(latch);
    }

    public void testClientNamedQueueRecovery() throws IOException, InterruptedException, TimeoutException {
        Channel ch = connection.createChannel();
        String q = "java-client.test.recovery.q1";
        declareClientNamedQueue(ch, q);
        closeAndWaitForRecovery();
        expectChannelRecovery(ch);
        expectQueueRecovery(ch, q);
        ch.queueDelete(q);
    }

    public void testServerNamedQueueRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare("", false, false, false, null).getQueue();
        String x = "amq.fanout";
        channel.queueBind(q, x, "");

        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        channel.basicPublish(x, "", null, "msg".getBytes());
        assertDelivered(q, 1);
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

    private String generateExchangeName() {
        return "java-client.test.recovery." + UUID.randomUUID().toString();
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
        AMQP.Queue.DeclareOk ok1 = channel.queueDeclarePassive(q);
        assertEquals(1, ok1.getConsumerCount());
        channel.basicCancel(tag);
        closeAndWaitForRecovery();
        expectChannelRecovery(channel);
        AMQP.Queue.DeclareOk ok2 = channel.queueDeclarePassive(q);
        assertEquals(0, ok2.getConsumerCount());
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

    public void testBasicAckAfterChannelRecovery() throws IOException, InterruptedException {
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
                        Host.closeConnection(connection);
                        recoveryLatch.await(30, TimeUnit.MINUTES);
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

    private AMQP.Queue.DeclareOk declareClientNamedQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, false, null);
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

    private CountDownLatch prepareForRecovery(Connection conn) {
        final CountDownLatch latch = new CountDownLatch(1);
        conn.addRecoveryListener(new RecoveryListener() {
            @Override
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
        });
        return latch;
    }

    private CountDownLatch prepareForShutdown(Connection conn) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        conn.addShutdownListener(new ShutdownListener() {
            @Override
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

    private AutorecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery) throws IOException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection();
    }

    private AutorecoveringConnection newRecoveringConnection(Address[] addresses) throws IOException {
        return newRecoveringConnection(false, addresses);
    }

    private AutorecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery, Address[] addresses) throws IOException {
        ConnectionFactory cf = buildConnectionFactoryWithRecoveryEnabled(disableTopologyRecovery);
        return (AutorecoveringConnection) cf.newConnection(addresses);
    }

    private ConnectionFactory buildConnectionFactoryWithRecoveryEnabled(boolean disableTopologyRecovery) {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        cf.setAutomaticRecovery(true);
        if (disableTopologyRecovery) {
            cf.setTopologyRecovery(false);
        }
        return cf;
    }

    private void wait(CountDownLatch latch) throws InterruptedException {
        // Very very generous amount of time to wait, just make sure we never
        // hang forever
        assertTrue(latch.await(30, TimeUnit.MINUTES));
    }

    private void waitForConfirms(Channel ch) throws InterruptedException, TimeoutException {
        ch.waitForConfirms(30 * 60 * 1000);
    }
}
