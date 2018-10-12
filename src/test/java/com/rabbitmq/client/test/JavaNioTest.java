package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.nio.BlockingQueueNioQueue;
import com.rabbitmq.client.impl.nio.DefaultByteBufferFactory;
import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class JavaNioTest {

    public static final String QUEUE = "nio.queue";

    private Connection testConnection;

    @Before
    public void init() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        testConnection = connectionFactory.newConnection();
    }

    @After
    public void tearDown() throws Exception {
        if (testConnection != null) {
            testConnection.createChannel().queueDelete(QUEUE);
            testConnection.close();
        }
    }

    @Test
    public void connection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        Connection connection = null;
        try {
            connection = basicGetBasicConsume(connectionFactory, "nio.queue", latch);
            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Message has not been received", messagesReceived);
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void twoConnections() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.setNioParams(new NioParams().setNbIoThreads(4));
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = basicGetBasicConsume(connectionFactory, "nio.queue.1", latch);
            connection2 = basicGetBasicConsume(connectionFactory, "nio.queue.2", latch);

            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Messages have not been received", messagesReceived);
        } finally {
            safeClose(connection1);
            safeClose(connection2);
        }
    }

    @Test
    public void twoConnectionsWithNioExecutor() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService nioExecutor = Executors.newFixedThreadPool(5);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        Connection connection1 = null;
        Connection connection2 = null;
        try {
            connection1 = basicGetBasicConsume(connectionFactory, "nio.queue.1", latch);
            connection2 = basicGetBasicConsume(connectionFactory, "nio.queue.2", latch);

            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Messages have not been received", messagesReceived);
        } finally {
            safeClose(connection1);
            safeClose(connection2);
            nioExecutor.shutdownNow();
        }
    }

    @Test
    public void shutdownListenerCalled() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        Connection connection = connectionFactory.newConnection();
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            connection.addShutdownListener(new ShutdownListener() {

                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    latch.countDown();
                }
            });
            safeClose(connection);
            assertTrue("Shutdown listener should have been called", latch.await(5, TimeUnit.SECONDS));
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void nioLoopCleaning() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        for(int i = 0; i < 10; i++) {
            Connection connection = connectionFactory.newConnection();
            connection.abort();
        }
    }

    @Test public void messageSize() throws Exception {
        for (int i = 0; i < 50; i++) {
            sendAndVerifyMessage(testConnection, 76390);
        }
    }

    @Test public void byteBufferFactory() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useNio();
        int baseCapacity = 32768;
        NioParams nioParams = new NioParams();
        nioParams.setReadByteBufferSize(baseCapacity / 2);
        nioParams.setWriteByteBufferSize(baseCapacity / 4);
        List<ByteBuffer> byteBuffers = new CopyOnWriteArrayList<>();
        cf.setNioParams(nioParams.setByteBufferFactory(new DefaultByteBufferFactory(capacity -> {
            ByteBuffer bb = ByteBuffer.allocate(capacity);
            byteBuffers.add(bb);
            return bb;
        })));

        try (Connection c = cf.newConnection()) {
            sendAndVerifyMessage(c, 100);
        }

        assertThat(byteBuffers, hasSize(2));
        assertThat(byteBuffers.get(0).capacity(), isOneOf(nioParams.getReadByteBufferSize(), nioParams.getWriteByteBufferSize()));
        assertThat(byteBuffers.get(1).capacity(), isOneOf(nioParams.getReadByteBufferSize(), nioParams.getWriteByteBufferSize()));
    }

    @Test public void directByteBuffers() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useNio();
        cf.setNioParams(new NioParams().setByteBufferFactory(new DefaultByteBufferFactory(capacity -> ByteBuffer.allocateDirect(capacity))));
        try (Connection c = cf.newConnection()) {
            sendAndVerifyMessage(c, 100);
        }
    }

    @Test public void customWriteQueue() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useNio();
        AtomicInteger count = new AtomicInteger(0);
        cf.setNioParams(new NioParams().setWriteQueueFactory(ctx -> {
            count.incrementAndGet();
            return new BlockingQueueNioQueue(
                new LinkedBlockingQueue<>(ctx.getNioParams().getWriteQueueCapacity()),
                ctx.getNioParams().getWriteEnqueuingTimeoutInMs()
            );
        }));
        try (Connection c = cf.newConnection()) {
            sendAndVerifyMessage(c, 100);
        }
        assertEquals(1, count.get());
    }

    private void sendAndVerifyMessage(Connection connection, int size) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        boolean messageReceived = basicGetBasicConsume(connection, QUEUE, latch, size);
        assertTrue("Message has not been received", messageReceived);
    }

    private Connection basicGetBasicConsume(ConnectionFactory connectionFactory, String queue, final CountDownLatch latch)
        throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, new byte[20000]);

        channel.basicConsume(queue, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                latch.countDown();
            }
        });

        return connection;
    }

    private boolean basicGetBasicConsume(Connection connection, String queue, final CountDownLatch latch, int msgSize)
        throws Exception {
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, new byte[msgSize]);

        final String tag = channel.basicConsume(queue, false, new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                latch.countDown();
            }
        });

        boolean done = latch.await(20, TimeUnit.SECONDS);
        channel.basicCancel(tag);
        return done;
    }

    private void safeClose(Connection connection) {
        if (connection != null) {
            try {
                connection.abort();
            } catch (Exception e) {
                // OK
            }
        }
    }
}
