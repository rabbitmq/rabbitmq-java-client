package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.recovery.RecoveringConnection;
import com.rabbitmq.tools.Host;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionRecovery extends TestCase {
    public static final int RECOVERY_INTERVAL = 50;

    public void testConnectionRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        assertTrue(c.isOpen());
        try {
            Host.closeConnection(c);
            expectConnectionRecovery(c);
        } finally {
            c.close();
        }
    }


    public void testConnectionRecoveryWithDisabledTopologyRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection(true);
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
        RecoveringConnection c = newRecoveringConnection();
        Channel ch1 = c.createChannel();
        Channel ch2 = c.createChannel();

        assertTrue(ch1.isOpen());
        assertTrue(ch2.isOpen());
        try {
            closeAndWaitForShutdown(c);
            assertFalse(ch1.isOpen());
            assertFalse(ch2.isOpen());
            waitForRecovery();
            expectChannelRecovery(ch1);
            expectChannelRecovery(ch2);
        } finally {
            c.close();
        }
    }

    public void testClientNamedQueueRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch = c.createChannel();
        String q = "java-client.test.recovery.q1";
        declareClientNamedQueue(ch, q);
        try {
            closeAndWaitForShutdown(c);
            assertFalse(ch.isOpen());
            waitForRecovery();
            expectChannelRecovery(ch);
            expectQueueRecovery(ch, q);
            ch.queueDelete(q);
        } finally {
            c.close();
        }
    }

    public void testServerNamedQueueRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch = c.createChannel();
        String q = ch.queueDeclare().getQueue();
        String x = "amq.fanout";
        ch.queueBind(q, x, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(latch);
        ch.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(c);
            expectChannelRecovery(ch);
            ch.basicPublish(x, "", null, "msg".getBytes());
            assertTrue(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            c.close();
        }
    }

    public void testExchangeToExchangeBindingRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch = c.createChannel();
        String q = ch.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = "java-client.test.recovery.x1";
        ch.exchangeDeclare(x2, "fanout");
        ch.exchangeBind(x1,x2, "");
        ch.queueBind(q, x1, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(latch);
        ch.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(c);
            expectChannelRecovery(ch);
            ch.basicPublish(x2, "", null, "msg".getBytes());
            assertTrue(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            ch.exchangeDelete(x2);
            c.close();
        }
    }

    public void testThatDeletedQueueBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch = c.createChannel();
        String q = ch.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = "java-client.test.recovery.x2";
        ch.exchangeDeclare(x2, "fanout");
        ch.exchangeBind(x1, x2, "");
        ch.queueBind(q, x1, "");
        ch.queueUnbind(q, x1, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(latch);
        ch.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(c);
            expectChannelRecovery(ch);
            ch.basicPublish(x2, "", null, "msg".getBytes());
            assertFalse(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            ch.exchangeDelete(x2);
            c.close();
        }
    }

    public void testThatDeletedExchangeBindingsDontReappearOnRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch = c.createChannel();
        String q = ch.queueDeclare().getQueue();
        String x1 = "amq.fanout";
        String x2 = "java-client.test.recovery.x2";
        ch.exchangeDeclare(x2, "fanout");
        ch.exchangeBind(x1, x2, "");
        ch.queueBind(q, x1, "");
        ch.exchangeUnbind(x1, x2, "");

        final CountDownLatch latch = new CountDownLatch(1);
        Consumer consumer = new CountingDownConsumer(latch);
        ch.basicConsume(q, consumer);
        try {
            closeAndWaitForRecovery(c);
            expectChannelRecovery(ch);
            ch.basicPublish(x2, "", null, "msg".getBytes());
            assertFalse(latch.await(150, TimeUnit.MILLISECONDS));
        } finally {
            ch.exchangeDelete(x2);
            c.close();
        }
    }

    private void closeAndWaitForShutdown(RecoveringConnection c) throws IOException, InterruptedException {
        Host.closeConnection(c);
        waitForShutdown();
    }

    private void closeAndWaitForRecovery(RecoveringConnection c) throws IOException, InterruptedException {
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

    private void expectConnectionRecovery(RecoveringConnection c) throws InterruptedException {
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

    private RecoveringConnection newRecoveringConnection() throws IOException {
        return newRecoveringConnection(false);
    }

    private RecoveringConnection newRecoveringConnection(boolean disableTopologyRecovery) throws IOException {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        final RecoveringConnection c = (RecoveringConnection) cf.newRecoveringConnection();
        if(disableTopologyRecovery) {
            c.disableAutomaticTopologyRecovery();
        }
        return c;
    }
}
