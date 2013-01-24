//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.examples;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.net.URISyntaxException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.SocketFrameHandler;
import com.rabbitmq.utility.BlockingCell;

public class TestMain {
    public static void main(String[] args) throws IOException, URISyntaxException {
        // Show what version this class was compiled with, to check conformance testing
        Class<?> clazz = TestMain.class;
        String javaVersion = System.getProperty("java.version");
        System.out.println(clazz.getName() + " : javac v" + getCompilerVersion(clazz) + " on " + javaVersion);
        try {
            boolean silent = Boolean.getBoolean("silent");
            final String uri = (args.length > 0) ? args[0] : "amqp://localhost";
            runConnectionNegotiationTest(uri);
            final Connection conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
            if (!silent) {
                System.out.println("Channel 0 fully open.");
            }

            new TestMain(conn, silent).run();

            runProducerConsumerTest(uri, 500);
            runProducerConsumerTest(uri, 0);
            runProducerConsumerTest(uri, -1);

            runConnectionShutdownTests(uri);

        } catch (Exception e) {
            System.err.println("Main thread caught exception: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class TestConnectionFactory extends ConnectionFactory {

        private final int protocolMajor;
        private final int protocolMinor;

        public TestConnectionFactory(int major, int minor, String uri)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
        {
            this.protocolMajor = major;
            this.protocolMinor = minor;
            setUri(uri);
        }

        protected FrameHandler createFrameHandler(Address addr)
            throws IOException {

            String hostName = addr.getHost();
            int portNumber = addr.getPort();
            if (portNumber == -1) portNumber = AMQP.PROTOCOL.PORT;
            return new SocketFrameHandler(getSocketFactory().createSocket(hostName, portNumber)) {
                    public void sendHeader() throws IOException {
                        sendHeader(protocolMajor, protocolMinor);
                    }
                };
        }
    }

    public static void runConnectionNegotiationTest(final String uri)
        throws IOException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {

        Connection conn;

        try {
            conn = new TestConnectionFactory(0, 1, uri).newConnection();
            conn.close();
            throw new RuntimeException("expected socket close");
        } catch (IOException e) {}

        ConnectionFactory factory;
        factory = new ConnectionFactory();
        factory.setUsername("invalid");
        factory.setPassword("invalid");
        try {
            factory.setUri(uri);
            conn = factory.newConnection();
            conn.close();
            throw new RuntimeException("expected socket close");
        } catch (IOException e) {}

        factory = new ConnectionFactory();
        factory.setRequestedChannelMax(10);
        factory.setRequestedFrameMax(8192);
        factory.setRequestedHeartbeat(1);
        factory.setUri(uri);
        conn = factory.newConnection();
        checkNegotiatedMaxValue("channel-max", 10, conn.getChannelMax());
        checkNegotiatedMaxValue("frame-max", 8192, conn.getFrameMax());
        checkNegotiatedMaxValue("heartbeat", 1, conn.getHeartbeat());
        conn.close();

        factory = new ConnectionFactory();
        factory.setRequestedChannelMax(0);
        factory.setRequestedFrameMax(0);
        factory.setRequestedHeartbeat(0);
        factory.setUri(uri);
        conn = factory.newConnection();
        checkNegotiatedMaxValue("channel-max", 0, conn.getChannelMax());
        checkNegotiatedMaxValue("frame-max", 0, conn.getFrameMax());
        checkNegotiatedMaxValue("heartbeat", 0, conn.getHeartbeat());
        conn.close();

        conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
        conn.close();
    }

    private static void checkNegotiatedMaxValue(String name,
                                                int requested,
                                                int negotiated) {
        if (requested != 0 && (negotiated == 0 || negotiated > requested)) {
            throw new RuntimeException("requested " + name + " of " +
                                       requested + ", negotiated " +
                                       negotiated);
        }
    }

    public static void runConnectionShutdownTests(final String uri)
        throws IOException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        Connection conn;
        Channel ch;
        // Test what happens when a connection is shut down w/o first
        // closing the channels.
        conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
        ch = conn.createChannel();
        conn.close();
        // Test what happens when we provoke an error
        conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
        ch = conn.createChannel();
        try {
            ch.exchangeDeclare("mumble", "invalid");
            throw new RuntimeException("expected shutdown");
        } catch (IOException e) {
        }
        // Test what happens when we just kill the connection
        conn = new ConnectionFactory(){{setUri(uri);}}.newConnection();
        ch = conn.createChannel();
        ((SocketFrameHandler)((AMQConnection)conn).getFrameHandler()).close();
    }

    public static void runProducerConsumerTest(String uri, int commitEvery)
        throws IOException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        ConnectionFactory cfconnp = new ConnectionFactory();
        cfconnp.setUri(uri);
        Connection connp = cfconnp.newConnection();
        ProducerMain p = new ProducerMain(connp, 2000, 10000, false, commitEvery, true);
        new Thread(p).start();
        ConnectionFactory cfconnc = new ConnectionFactory();
        cfconnc.setUri(uri);
        Connection connc = cfconnc.newConnection();
        ConsumerMain c = new ConsumerMain(connc, false, true);
        c.run();
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException _) { } // ignore
    }

    private Connection _connection;

    private Channel _ch1;

    private int _messageId = 0;

    private final boolean _silent;

    private volatile BlockingCell<Object> returnCell;

    public TestMain(Connection connection, boolean silent) {
        _connection = connection;
        _silent = silent;
    }

    public Channel createChannel() throws IOException {
        return _connection.createChannel();
    }

    public void log(String s) {
        if (!_silent)
            System.out.println(s);
    }

    public void run() throws IOException {
        final int batchSize = 5;

        _ch1 = createChannel();

        String queueName =_ch1.queueDeclare().getQueue();

        sendLotsOfTrivialMessages(batchSize, queueName);
        expect(batchSize, drain(batchSize, queueName, false));

        BlockingCell<Object> k1 = new BlockingCell<Object>();
        BlockingCell<Object> k2 = new BlockingCell<Object>();
        String cTag1 = _ch1.basicConsume(queueName, true, new BatchedTracingConsumer(true, k1, batchSize, _ch1));
        String cTag2 = _ch1.basicConsume(queueName, false, new BatchedTracingConsumer(false, k2, batchSize, _ch1));
        sendLotsOfTrivialMessages(batchSize, queueName);
        sendLotsOfTrivialMessages(batchSize, queueName);

        k1.uninterruptibleGet();
        k2.uninterruptibleGet();
        _ch1.basicCancel(cTag1);
        _ch1.basicCancel(cTag2);

        tryTopics();

        queueName =_ch1.queueDeclare().getQueue();
        sendLotsOfTrivialMessages(batchSize, queueName);
        expect(batchSize, drain(batchSize, queueName, true));

        _ch1.close();

        log("Closing.");
        try {
            _connection.close();
        } catch (IllegalStateException e) {
            // work around bug 15794
        }
        log("Leaving TestMain.run().");
    }

    private void setChannelReturnListener() {
        log("Setting return listener..");
        _ch1.addReturnListener(new ReturnListener() {
            public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {
                Method method = new AMQP.Basic.Return.Builder()
                                        .replyCode(replyCode)
                                        .replyText(replyText)
                                        .exchange(exchange)
                                        .routingKey(routingKey)
                                .build();
                log("Handling return with body " + new String(body));
                TestMain.this.returnCell.set(new Object[] { method, properties, body });
            }
        });
    }

    public class UnexpectedSuccessException extends IOException {
        /**
         * Default version UID for serializable class
         */
        private static final long serialVersionUID = 1L;

        public UnexpectedSuccessException() {
            // no special handling needed for default constructor
        }
    }

    public class TracingConsumer extends DefaultConsumer {
        public TracingConsumer(Channel ch) {
            super(ch);
        }

        @Override public void handleConsumeOk(String c) {
            log(this + ".handleConsumeOk(" + c + ")");
            super.handleConsumeOk(c);
        }

        @Override public void handleCancelOk(String c) {
            log(this + ".handleCancelOk(" + c + ")");
            super.handleCancelOk(c);
        }

        @Override public void handleShutdownSignal(String c, ShutdownSignalException sig) {
            log(this + ".handleShutdownSignal(" + c + ", " + sig + ")");
            super.handleShutdownSignal(c, sig);
        }
    }

    public class BatchedTracingConsumer extends TracingConsumer {
        final boolean _autoAck;

        final BlockingCell<Object> _k;

        final int _batchSize;

        int _counter;

        public BatchedTracingConsumer(boolean autoAck, BlockingCell<Object> k, int batchSize, Channel ch) {
            super(ch);
            _autoAck = autoAck;
            _k = k;
            _batchSize = batchSize;
            _counter = 0;
        }

        @Override public void handleDelivery(String consumer_Tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            log("Async message (" + _counter + "," + (_autoAck ? "autoack" : "ack") + "): " + new String(body));
            _counter++;
            if (_counter == _batchSize) {
                if (!_autoAck) {
                    log("Acking batch.");
                    getChannel().basicAck(envelope.getDeliveryTag(), true);
                }
                _k.set(new Object());
            }
        }
    }

    public void sendLotsOfTrivialMessages(int batchSize, String routingKey) throws IOException {
        for (int i = 0; i < batchSize; i++) {
            String messageText = "(" + _messageId + ") On the third tone, the time will be " + new java.util.Date();
            _messageId++;
            publish1("", routingKey, messageText);
            // sleep(200);
        }
    }

    public void expect(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", but actually got " + actual);
        }
    }

    public void assertNull(Object o) {
        if (o != null) {
            throw new AssertionError("Expected null object, got " + o);
        }
    }

    public void assertNonNull(Object o) {
        if (o == null) {
            throw new AssertionError("Expected non-null object");
        }
    }

    public int drain(int batchSize, String queueName, boolean autoAck) throws IOException {
        long latestTag = 0;
        boolean notEmpty = true;
        int remaining = batchSize;
        int count = 0;

        while (notEmpty && (remaining > 0)) {
            for (int i = 0; (i < 2) && (remaining > 0); i++) {
                GetResponse c = _ch1.basicGet(queueName, autoAck);
                if (c == null) {
                    notEmpty = false;
                } else {
                    String msg = new String(c.getBody());
                    log("Got message (" + c.getMessageCount() + " left in q): " + msg);
                    latestTag = c.getEnvelope().getDeliveryTag();
                    remaining--;
                    count++;
                }
            }
            if (!autoAck && latestTag != 0) {
                _ch1.basicAck(latestTag, true);
                latestTag = 0;
            }
        }
        log("Drained, remaining in batch = " + remaining + ".");
        return count;
    }

    public void publish1(String x, String routingKey, String body) throws IOException {
        _ch1.basicPublish(x, routingKey, MessageProperties.TEXT_PLAIN, body.getBytes());
    }

    public void publish2(String x, String routingKey, String body) throws IOException {
        _ch1.basicPublish(x, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, body.getBytes());
    }

    public void tryTopics() throws IOException {
        String q1 = "tryTopicsQueue1";
        String q2 = "tryTopicsQueue2";
        String q3 = "tryTopicsQueue3";
        String x = "tryTopicsExch";
        _ch1.queueDeclare(q1, false, true, true, null);
        _ch1.queueDeclare(q2, false, true, true, null);
        _ch1.queueDeclare(q3, false, true, true, null);
        _ch1.exchangeDeclare(x, "topic", false, true, null);
        _ch1.queueBind(q1, x, "test.#");
        _ch1.queueBind(q2, x, "test.test");
        _ch1.queueBind(q3, x, "*.test.#");

        log("About to publish to topic queues");
        publish1(x, "", "A"); // no binding matches
        publish1(x, "test", "B"); // matches q1
        publish1(x, "test.test", "C"); // matches q1, q2, q3
        publish1(x, "test.test.test", "D"); // matches q1, q3

        log("About to drain q1");
        expect(3, drain(10, q1, true));
        log("About to drain q2");
        expect(1, drain(10, q2, true));
        log("About to drain q3");
        expect(2, drain(10, q3, true));
    }

    public void doBasicReturn(BlockingCell<Object> cell, int expectedCode) {
        Object[] a = (Object[]) cell.uninterruptibleGet();
        AMQP.Basic.Return method = (AMQP.Basic.Return) a[0];
        log("Returned: " + method);
        log(" - props: " + a[1]);
        log(" - body: " + new String((byte[]) a[2]));
        int replyCode = method.getReplyCode();
        if (replyCode != expectedCode) {
            System.err.println("Eek! Got basic return with code " + replyCode + ", but expected code " + expectedCode);
            System.exit(1);
        }
    }

    private void unsetChannelReturnListener() {
        _ch1.clearReturnListeners();
        log("ReturnListeners unset");
    }

    public void waitForKey(String prompt) throws IOException {
        if (!_silent) {
            System.out.println(prompt);
            System.out.println("[Press return to continue]");
            while (System.in.read() != 10) {
                // do nothing
            }
        }
    }

    // utility: tell what Java compiler version a class was compiled with
    public static String getCompilerVersion(Class<?> clazz) throws IOException {
        String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
        System.out.println(resourceName);

        // get binary class data
        InputStream in = clazz.getResourceAsStream(resourceName);

        // skip over the magic number (todo: make sure it's 0xCAFEBABE - check endedness)
        if (in.skip(4) != 4) {
            throw new IOException("found incorrect magic number in class file");
        }
        int minor = (in.read() << 8) + in.read();
        int major = (in.read() << 8) + in.read();
        in.close();

        return major + "." + minor;
    }
}
