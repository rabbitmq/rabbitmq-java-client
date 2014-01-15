package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.impl.recovery.Recoverable;
import com.rabbitmq.client.impl.recovery.RecoveryListener;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionRecovery extends BrokerTestCase {
    public static final int RECOVERY_INTERVAL = 50;
    protected AutorecoveringConnection connection;

    public void testConnectionRecovery() throws IOException, InterruptedException {
        assertTrue(connection.isOpen());
        Host.closeConnection(connection);
        expectConnectionRecovery(connection);
    }

    public void testConnectionRecoveryWithDisabledTopologyRecovery() throws IOException, InterruptedException {
        AutorecoveringConnection c = newRecoveringConnection(true);
        Channel ch = c.createChannel();
        String q = "java-client.test.recovery.q2";
        ch.queueDeclare(q, false, true, false, null);
        ch.queueDeclarePassive(q);
        assertTrue(c.isOpen());
        try {
            Host.closeConnection(c);
            expectConnectionRecovery(c);
            ch.queueDeclarePassive(q);
            fail("expected passive declaration to throw");
        } catch (java.io.IOException e) {
            // expected
        } finally {
            c.close();
        }
    }


    public void testChannelRecovery() throws IOException, InterruptedException {
        Channel ch1 = connection.createChannel();
        Channel ch2 = connection.createChannel();

        assertTrue(ch1.isOpen());
        assertTrue(ch2.isOpen());
        closeAndWaitForShutdown(connection);
        assertFalse(ch1.isOpen());
        assertFalse(ch2.isOpen());
        waitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
    }

    public void testClientNamedQueueRecovery() throws IOException, InterruptedException {
        Channel ch = connection.createChannel();
        String q = "java-client.test.recovery.q1";
        declareClientNamedQueue(ch, q);
        closeAndWaitForShutdown(connection);
        assertFalse(ch.isOpen());
        waitForRecovery();
        expectChannelRecovery(ch);
        expectQueueRecovery(ch, q);
        ch.queueDelete(q);
    }

    public void testServerNamedQueueRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        String x = "amq.fanout";
        channel.queueBind(q, x, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(channel, latch);
        channel.basicConsume(q, consumer);
        closeAndWaitForRecovery(connection);
        expectChannelRecovery(channel);
        channel.basicPublish(x, "", null, "msg".getBytes());
        assertTrue(latch.await(150, TimeUnit.MILLISECONDS));
    }

    public void testExchangeToExchangeBindingRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(channel, latch);
        channel.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(connection);
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertTrue(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            channel.exchangeDelete(x2);
        }
    }

    private String generateExchangeName() {
        return "java-client.test.recovery." + UUID.randomUUID().toString();
    }

    public void testThatDeletedQueueBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");
        channel.queueUnbind(q, x1, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(channel, latch);
        channel.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(connection);
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertFalse(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            channel.exchangeDelete(x2);
        }
    }

    public void testThatDeletedExchangeBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        String q = channel.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = generateExchangeName();
        channel.exchangeDeclare(x2, "fanout");
        channel.exchangeBind(x1, x2, "");
        channel.queueBind(q, x1, "");
        channel.exchangeUnbind(x1, x2, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(channel, latch);
        channel.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(connection);
            expectChannelRecovery(channel);
            channel.basicPublish(x2, "", null, "msg".getBytes());
            assertFalse(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            channel.exchangeDelete(x2);
        }
    }

    public void testThatDeletedExchangeDoesNotReappearOnRecover() throws IOException, InterruptedException {
        String x = generateExchangeName();
        channel.exchangeDeclare(x, "fanout");
        channel.exchangeDelete(x);
        try {
            closeAndWaitForRecovery(connection);
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
            closeAndWaitForRecovery(connection);
            expectChannelRecovery(channel);
            channel.queueDeclarePassive(q);
            fail("Expected passive declare to fail");
        } catch (IOException ioe) {
            // expected
        }
    }

    public void testConnectionRecoveryCallback() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        connection.addRecoveryListener(new RecoveryListener() {
            public void handleRecovery(Recoverable recoverable) {
                latch.countDown();
            }
        });
        assertTrue(connection.isOpen());
        Host.closeConnection(connection);
        expectConnectionRecovery(connection);
        assertTrue(latch.await(50, TimeUnit.MILLISECONDS));
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
        closeAndWaitForShutdown(connection);
        waitForRecovery();
        expectChannelRecovery(ch1);
        expectChannelRecovery(ch2);
        assertTrue(latch.await(50, TimeUnit.MILLISECONDS));
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
                        // Imitate some work
                        Thread.sleep(200);
                        Host.closeConnection(connection);
                        waitForRecovery();
                    }
                    channel.basicAck(envelope.getDeliveryTag(), false);
                } catch (InterruptedException e) {
                    // ignore
                }
                finally {
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
            // publish messages at intervals that allow recovery to finish
            Thread.sleep(150);
            publishingChannel.basicPublish("", q, null, "msg".getBytes());
        }
        assertTrue(latch.await(n, TimeUnit.SECONDS));
    }

    private void closeAndWaitForShutdown(AutorecoveringConnection c) throws IOException, InterruptedException {
        Host.closeConnection(c);
        waitForShutdown();
    }

    private void closeAndWaitForRecovery(AutorecoveringConnection c) throws IOException, InterruptedException {
        Host.closeConnection(c);
        waitForRecovery();
    }

    private AMQP.Queue.DeclareOk declareClientNamedQueue(Channel ch, String q) throws IOException {
        return ch.queueDeclare(q, true, false, false, null);
    }

    private void waitForShutdown() throws InterruptedException {
        Thread.sleep(20);
    }

    private void expectQueueRecovery(Channel ch, String q) throws IOException, InterruptedException {
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = declareClientNamedQueue(ch, q);
        assertEquals(0, ok1.getMessageCount());
        ch.basicPublish("", q, null, "msg".getBytes());
        Thread.sleep(20);
        AMQP.Queue.DeclareOk ok2 = declareClientNamedQueue(ch, q);
        assertEquals(1, ok2.getMessageCount());
    }

    private void expectConnectionRecovery(AutorecoveringConnection c) throws InterruptedException {
        String oldName = c.getName();
        waitForRecovery();
        assertTrue(c.isOpen());
        assertFalse(oldName.equals(c.getName()));
    }

    private void waitForRecovery() throws InterruptedException {
        Thread.sleep(RECOVERY_INTERVAL + 150);
    }

    private void expectChannelRecovery(Channel ch) throws InterruptedException {
        assertTrue(ch.isOpen());
    }

    private AutorecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery) throws IOException {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        cf.setAutomaticRecovery(true);
        final AutorecoveringConnection c = (AutorecoveringConnection) cf.newConnection();
        if(disableTopologyRecovery) {
            c.disableAutomaticTopologyRecovery();
        }
        return c;
    }

    protected void setUp()
            throws IOException {
        openConnection();
        openChannel();
    }

    protected void tearDown()
            throws IOException {
        closeChannel();
        closeConnection();
    }

    @Override
    public void openConnection() throws IOException {
        connection = newRecoveringConnection(false);
    }

    @Override
    public void openChannel()
            throws IOException {
        channel = connection.createChannel();
    }

    @Override
    public void closeConnection() throws IOException {
        if(connection.isOpen()) {
            connection.close();
        }
    }

    @Override
    public void closeChannel() throws IOException {
        if(channel.isOpen()) {
            channel.close();
        }
    }
}
