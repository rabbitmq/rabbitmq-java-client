package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.recovery.RecoveringConnection;
import com.rabbitmq.tools.Host;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.List;

public class ConnectionRecovery extends TestCase {
    public static final int RECOVERY_INTERVAL = 50;

    public void testConnectionRecovery() throws IOException, InterruptedException {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setNetworkRecoveryInterval(RECOVERY_INTERVAL);
        RecoveringConnection c = (RecoveringConnection) cf.newRecoveringConnection();
        Host.ConnectionInfo ci = findConnectionInfoFor(Host.listConnections(), c);

        assertTrue(c.isOpen());
        try {
            Host.closeConnection(ci.getPid());
            expectConnectionRecovery(RECOVERY_INTERVAL, c);
        } finally {
            c.abort();
        }
    }

    private void expectConnectionRecovery(int recoveryInterval, RecoveringConnection c) throws InterruptedException {
        String oldName = c.getName();
        Thread.sleep(20);
        assertFalse(c.isOpen());
        Thread.sleep(recoveryInterval + 100);
        assertTrue(c.isOpen());
        assertFalse(oldName.equals(c.getName()));
    }

    private Host.ConnectionInfo findConnectionInfoFor(List<Host.ConnectionInfo> xs, RecoveringConnection c) {
        Host.ConnectionInfo result = null;
        for (Host.ConnectionInfo ci : xs) {
            if(c.getName().equals(ci.getName())){
                result = ci;
                break;
            }
        }
        return result;
    }
}
