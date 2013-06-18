package com.rabbitmq.client.test.server;

import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BlockedConnection extends BrokerTestCase {
    protected void releaseResources() throws IOException {
        unblock();
    }
    public void testBlock() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        Connection connection = connection(latch);
        block();
        publish(connection);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    public void testInitialBlock() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        block();
        Connection connection = connection(latch);
        publish(connection);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private void block() throws IOException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.000000001");
    }

    private void unblock() throws IOException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.4");
    }

    private Connection connection(final CountDownLatch latch) throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        connection.addBlockedListener(new BlockedListener() {
            public void handleBlocked(String reason) throws IOException {
                unblock();
            }

            public void handleUnblocked() throws IOException {
                latch.countDown();
            }
        });
        return connection;
    }

    private void publish(Connection connection) throws IOException {
        Channel ch = connection.createChannel();
        ch.basicPublish("", "", MessageProperties.BASIC, "".getBytes());
    }
}
