package com.rabbitmq.examples;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class DirectReplyToPerformance {
    private static final String DIRECT_QUEUE = "amq.rabbitmq.reply-to";
    private static final String SERVER_QUEUE = "server-queue";
    private static final int CLIENTS = 5;
    private static final int RPC_COUNT_PER_CLIENT = 2000;

    public static void main(String[] args) throws Exception {
        String uri = args[0];
        start(new Server(uri));

        doTest(uri, DirectReply.class,      true);
        doTest(uri, SharedReplyQueue.class, true);
        doTest(uri, PerRPCReplyQueue.class, true);
        doTest(uri, DirectReply.class,      false);
        doTest(uri, SharedReplyQueue.class, false);
        doTest(uri, PerRPCReplyQueue.class, false);
        System.exit(0);
    }

    private static void doTest(String uri, Class strategy, boolean reuseConnection) throws Exception {
        System.out.println("*** " + strategy.getSimpleName() + (reuseConnection ? " (reusing connections)" : ""));
        CountDownLatch latch = new CountDownLatch(CLIENTS);
        for (int i = 0; i < CLIENTS; i++) {
            start(new Client(uri, latch, (ReplyQueueStrategy) strategy.newInstance(), reuseConnection));
        }
        latch.await();
    }

    private static void start(final Task task) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }).start();
    }

    private interface Task {
        public void run() throws Exception;
    }

    private interface ReplyQueueStrategy {
        public String preMsg(Channel ch, Consumer consumer) throws IOException;
        public void postMsg(Channel ch) throws IOException;
    }

    public static class DirectReply implements ReplyQueueStrategy {
        private String ctag;

        public String preMsg(Channel ch, Consumer consumer) throws IOException {
            ctag = ch.basicConsume(DIRECT_QUEUE, true, consumer);
            return DIRECT_QUEUE;
        }

        public void postMsg(Channel ch) throws IOException {
            ch.basicCancel(ctag);
        }
    }

    public static class SharedReplyQueue implements ReplyQueueStrategy {
        private final String queue;
        private String ctag;

        public SharedReplyQueue() {
            queue = "reply-queue-" + UUID.randomUUID();
        }

        public String preMsg(Channel ch, Consumer consumer) throws IOException {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("x-expires", 10000);
            ch.queueDeclare(queue, false, false, false, args);
            ctag = ch.basicConsume(queue, true, consumer);
            return queue;
        }

        public void postMsg(Channel ch) throws IOException {
            ch.basicCancel(ctag);
        }
    }

    public static class PerRPCReplyQueue implements ReplyQueueStrategy {
        private String queue;

        public String preMsg(Channel ch, Consumer consumer) throws IOException {
            queue = ch.queueDeclare().getQueue();
            ch.basicConsume(queue, true, consumer);
            return queue;
        }

        public void postMsg(Channel ch) throws IOException {
            ch.queueDelete(queue);
        }
    }
    private static class Server implements Task {
        private final String uri;

        public Server(String uri) {
            this.uri = uri;
        }

        public void run() throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            Connection connection = factory.newConnection();
            final Channel ch = connection.createChannel();
            ch.queueDeclare(SERVER_QUEUE, false, true, false, null);
            ch.basicConsume(SERVER_QUEUE, true, new DefaultConsumer(ch) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String replyTo = properties.getReplyTo();
                    ch.basicPublish("", replyTo, MessageProperties.MINIMAL_BASIC, "Hello client!".getBytes());
                }
            });
        }
    }

    private static class Client implements Task {
        private final String uri;
        private final CountDownLatch globalLatch;
        private final ReplyQueueStrategy strategy;
        private final boolean reuseConnection;

        public Client(String uri, CountDownLatch latch, ReplyQueueStrategy strategy, boolean reuseConnection) {
            this.uri = uri;
            this.globalLatch = latch;
            this.strategy = strategy;
            this.reuseConnection = reuseConnection;
        }

        public void run() throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(uri);
            final CountDownLatch[] latch = new CountDownLatch[1];
            long time = System.nanoTime();
            Consumer cons = new ClientConsumer(latch);
            Connection conn = null;
            Channel ch = null;
            if (reuseConnection) {
                conn = factory.newConnection();
                ch = conn.createChannel();
            }
            for (int i = 0; i < RPC_COUNT_PER_CLIENT; i++) {
                latch[0] = new CountDownLatch(1);
                if (!reuseConnection) {
                    conn = factory.newConnection();
                    ch = conn.createChannel();
                }

                String replyTo = strategy.preMsg(ch, cons);
                AMQP.BasicProperties props = MessageProperties.MINIMAL_BASIC.builder().replyTo(replyTo).build();
                ch.basicPublish("", SERVER_QUEUE, props, "Hello server!".getBytes());
                latch[0].await();
                strategy.postMsg(ch);
                if (!reuseConnection) {
                    conn.close();
                }
            }
            if (reuseConnection) {
                conn.close();
            }
            System.out.println((System.nanoTime() - time) / (1000 * RPC_COUNT_PER_CLIENT) + "us per RPC");
            globalLatch.countDown();
        }
    }

    private static class ClientConsumer implements Consumer {
        private final CountDownLatch[] latch;

        public ClientConsumer(CountDownLatch[] latch) {
            this.latch = latch;
        }

        @Override public void handleConsumeOk(String consumerTag) {}
        @Override public void handleCancelOk(String consumerTag) {}
        @Override public void handleCancel(String consumerTag) throws IOException {}
        @Override public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {}
        @Override public void handleRecoverOk(String consumerTag) {}

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            latch[0].countDown();
        }
    }

}
