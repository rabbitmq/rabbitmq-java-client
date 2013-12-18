package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.recovery.RecoveringConnection;
import com.rabbitmq.tools.Host;
import junit.framework.TestCase;

import java.io.IOException;

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

    public void testChannelRecovery() throws IOException, InterruptedException {
        RecoveringConnection c = newRecoveringConnection();
        Channel ch1 = c.createChannel();
        Channel ch2 = c.createChannel();

        assertTrue(ch1.isOpen());
        assertTrue(ch2.isOpen());
        try {
            Host.closeConnection(c);
            waitForShutdown();
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
        ch.queueDeclare(q, true, false, false, null);
        try {
            Host.closeConnection(c);
            waitForShutdown();
            assertFalse(ch.isOpen());
            waitForRecovery();
            expectChannelRecovery(ch);
            expectQueueRecovery(ch, q);
            ch.queueDelete(q);
        } finally {
            c.close();
        }
    }

    private void waitForShutdown() throws InterruptedException {
        Thread.sleep(20);
    }

    private void expectQueueRecovery(Channel ch, String q) throws IOException, InterruptedException {
        ch.queuePurge(q);
        AMQP.Queue.DeclareOk ok1 = ch.queueDeclare(q, true, false, false, null);
        assertEquals(0, ok1.getMessageCount());
        ch.basicPublish("", q, null, "msg".getBytes());
        Thread.sleep(20);
        AMQP.Queue.DeclareOk ok2 = ch.queueDeclare(q, true, false, false, null);
        assertEquals(1, ok2.getMessageCount());
    }

    private void expectConnectionRecovery(RecoveringConnection c) throws InterruptedException {
        String oldName = c.getName();
        waitForShutdown();
        assertFalse(c.isOpen());
        waitForRecovery();
        assertTrue(c.isOpen());
        assertFalse(oldName.equals(c.getName()));
    }

    private void waitForRecovery() throws InterruptedException {
        Thread.sleep(RECOVERY_INTERVAL + 100);
    }

    private void expectChannelRecovery(Channel ch) throws InterruptedException {
        assertTrue(ch.isOpen());
    }

    private RecoveringConnection newRecoveringConnection() throws IOException {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        return (RecoveringConnection) cf.newRecoveringConnection();
    }

}
