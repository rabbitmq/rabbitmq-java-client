// Copyright (c) 2007-2022 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.tools.Host;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

public class TestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public static final boolean USE_NIO = System.getProperty("use.nio") != null;

    public static ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        if (USE_NIO) {
            connectionFactory.useNio();
        } else {
            connectionFactory.useBlockingIo();
        }
        return connectionFactory;
    }

    @FunctionalInterface
    public interface CallableBooleanSupplier {

        boolean getAsBoolean() throws Exception;

    }

    public static void waitAtMost(CallableBooleanSupplier condition) {
       waitAtMost(Duration.ofSeconds(10), condition);
    }

    public static void waitAtMost(Duration timeout, CallableBooleanSupplier condition) {
        try {
            if (condition.getAsBoolean()) {
                return;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int waitTime = 100;
        int waitedTime = 0;
        long timeoutInMs = timeout.toMillis();
        while (waitedTime <= timeoutInMs) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            waitedTime += waitTime;
        }
        Assertions.fail("Waited " + timeout.getSeconds() + " second(s), condition never got true");
    }

    public static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void abort(Connection connection) {
        if (connection != null) {
            connection.abort();
        }
    }

    public static TestRule atLeast38() {
        return new BrokerVersionTestRule("3.8.0");
    }

    public static TestRule brokerIsNotRunningOnDocker() {
        return new BrokerIsNotOnDocker();
    }

    public static boolean isVersion37orLater(Connection connection) {
        return atLeastVersion("3.7.0", connection);
    }

    public static boolean isVersion38orLater(Connection connection) {
        return atLeastVersion("3.8.0", connection);
    }

    public static boolean isVersion310orLater(Connection connection) {
        return atLeastVersion("3.10.0", connection);
    }

    private static boolean atLeastVersion(String expectedVersion, Connection connection) {
        String currentVersion = null;
        try {
            currentVersion = currentVersion(
                    connection.getServerProperties().get("version").toString()
            );
            return "0.0.0".equals(currentVersion) || versionCompare(currentVersion, expectedVersion) >= 0;
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(TestUtils.class).warn("Unable to parse broker version {}", currentVersion, e);
            throw e;
        }
    }

    private static String currentVersion(String currentVersion) {
        // versions built from source: 3.7.0+rc.1.4.gedc5d96
        if (currentVersion.contains("+")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("+"));
        }
        // alpha (snapshot) versions: 3.7.0~alpha.449-1
        if (currentVersion.contains("~")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("~"));
        }
        // alpha (snapshot) versions: 3.7.1-alpha.40
        if (currentVersion.contains("-")) {
            currentVersion = currentVersion.substring(0, currentVersion.indexOf("-"));
        }
        return currentVersion;
    }

    public static boolean sendAndConsumeMessage(String exchange, String routingKey, String queue, Connection c)
            throws IOException, TimeoutException, InterruptedException {
        Channel ch = c.createChannel();
        try {
            ch.confirmSelect();
            final CountDownLatch latch = new CountDownLatch(1);
            ch.basicConsume(queue, true, new DefaultConsumer(ch) {

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    latch.countDown();
                }
            });
            ch.basicPublish(exchange, routingKey, null, "".getBytes());
            ch.waitForConfirmsOrDie(5000);
            return latch.await(5, TimeUnit.SECONDS);
        } finally {
            if (ch != null && ch.isOpen()) {
                ch.close();
            }
        }
    }

    public static boolean resourceExists(Callable<Channel> callback) throws Exception {
        Channel declarePassiveChannel = null;
        try {
            declarePassiveChannel = callback.call();
            return true;
        } catch (IOException e) {
            if (e.getCause() instanceof ShutdownSignalException) {
                ShutdownSignalException cause = (ShutdownSignalException) e.getCause();
                if (cause.getReason() instanceof AMQP.Channel.Close) {
                    if (((AMQP.Channel.Close) cause.getReason()).getReplyCode() == 404) {
                        return false;
                    } else {
                        throw e;
                    }
                }
                return false;
            } else {
                throw e;
            }
        } finally {
            if (declarePassiveChannel != null && declarePassiveChannel.isOpen()) {
                declarePassiveChannel.close();
            }
        }
    }

    public static boolean queueExists(final String queue, final Connection connection) throws Exception {
        return resourceExists(() -> {
            Channel channel = connection.createChannel();
            channel.queueDeclarePassive(queue);
            return channel;
        });
    }

    public static boolean exchangeExists(final String exchange, final Connection connection) throws Exception {
        return resourceExists(() -> {
            Channel channel = connection.createChannel();
            channel.exchangeDeclarePassive(exchange);
            return channel;
        });
    }

    public static void closeAndWaitForRecovery(RecoverableConnection connection) throws IOException, InterruptedException {
        CountDownLatch latch = prepareForRecovery(connection);
        Host.closeConnection((NetworkConnection) connection);
        wait(latch);
    }

    public static void closeAllConnectionsAndWaitForRecovery(Collection<Connection> connections) throws IOException, InterruptedException {
        CountDownLatch latch = prepareForRecovery(connections);
        Host.closeAllConnections();
        wait(latch);
    }

    public static void closeAllConnectionsAndWaitForRecovery(Connection connection) throws IOException, InterruptedException {
        closeAllConnectionsAndWaitForRecovery(Collections.singletonList(connection));
    }

    public static CountDownLatch prepareForRecovery(Connection connection) {
        return prepareForRecovery(Collections.singletonList(connection));
    }

    public static CountDownLatch prepareForRecovery(Collection<Connection> connections) {
        final CountDownLatch latch = new CountDownLatch(connections.size());
        for (Connection conn : connections) {
            ((AutorecoveringConnection) conn).addRecoveryListener(new RecoveryListener() {

                @Override
                public void handleRecovery(Recoverable recoverable) {
                    latch.countDown();
                }

                @Override
                public void handleRecoveryStarted(Recoverable recoverable) {
                    // No-op
                }
            });
        }
        return latch;
    }

    private static void wait(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(90, TimeUnit.SECONDS));
    }

    /**
     * https://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
     */
    static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

    public static int randomNetworkPort() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.bind(null);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private static class BrokerVersionTestRule implements TestRule {

        private final String version;

        public BrokerVersionTestRule(String version) {
            this.version = version;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try (Connection c = TestUtils.connectionFactory().newConnection()) {
                        if (!TestUtils.atLeastVersion(version, c)) {
                            throw new AssumptionViolatedException("Broker version < " + version + ", skipping.");
                        }
                    }
                    base.evaluate();
                }
            };
        }
    }

    private static class BrokerIsNotOnDocker implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        if (Host.isOnDocker()) {
                            throw new AssumptionViolatedException("Broker is running on Docker");
                        }
                    } catch (AssumptionViolatedException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new AssumptionViolatedException("Could not check whether broker is running on Docker or not", e);
                    }
                    base.evaluate();
                }
            };
        }
    }

    @FunctionalInterface
    public interface CallableFunction<T, R> {

        R apply(T t) throws Exception;

    }

    public static boolean basicGetBasicConsume(Connection connection, String queue, final CountDownLatch latch, int msgSize)
        throws Exception {
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, true, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, new byte[msgSize]);

        String tag = channel.basicConsume(queue, false, new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                latch.countDown();
            }
        });

        boolean messageReceived = latch.await(20, TimeUnit.SECONDS);

        channel.basicCancel(tag);

        return messageReceived;
    }

    public static class DefaultTestSuite extends Suite {


        public DefaultTestSuite(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
            super(klass, builder);
        }

        public DefaultTestSuite(RunnerBuilder builder, Class<?>[] classes)
            throws InitializationError {
            super(builder, classes);
        }

        protected DefaultTestSuite(Class<?> klass, Class<?>[] suiteClasses)
            throws InitializationError {
            super(klass, suiteClasses);
        }

        protected DefaultTestSuite(RunnerBuilder builder, Class<?> klass, Class<?>[] suiteClasses)
            throws InitializationError {
            super(builder, klass, suiteClasses);
        }

        @Override
        protected void runChild(Runner runner, RunNotifier notifier) {
            LOGGER.info("Running test {}", runner.getDescription().getDisplayName());
            super.runChild(runner, notifier);
        }

        protected DefaultTestSuite(Class<?> klass, List<Runner> runners)
            throws InitializationError {
            super(klass, runners);
        }
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TestExecutionCondition {

        Class<? extends ExecutionCondition>[] value();

    }

    interface ExecutionCondition {

        void check(Description description) throws Exception;

    }

    public static class BrokerAtLeast310Condition implements ExecutionCondition {

        private static final String VERSION = "3.10.0";

        @Override
        public void check(Description description) throws Exception {
            try (Connection c = TestUtils.connectionFactory().newConnection()) {
                if (!TestUtils.atLeastVersion(VERSION, c)) {
                    throw new AssumptionViolatedException("Broker version < " + VERSION + ", skipping.");
                }
            }
        }
    }

    public static class ExecutionConditionRule implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Method testMethod = description.getTestClass().getDeclaredMethod(description.getMethodName());
                    TestExecutionCondition conditionAnnotation = testMethod.getAnnotation(
                        TestExecutionCondition.class);
                    if (conditionAnnotation != null) {
                       conditionAnnotation.value()[0].getConstructor().newInstance()
                           .check(description);
                    }
                    base.evaluate();
                }
            };
        }
    }

    public static TestRule atLeastJava11() {
        return new AtLeastJavaVersion(11);
    }

    private static class AtLeastJavaVersion implements TestRule {

        private final int expectedMinVersion;

        private AtLeastJavaVersion(int expectedMinVersion) {
            this.expectedMinVersion = expectedMinVersion;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        int javaMajorVersion = javaMajorVersion();
                        if (javaMajorVersion < expectedMinVersion) {
                            throw new AssumptionViolatedException("Java version is " + javaMajorVersion
                                + ", expecting at least " + expectedMinVersion);
                        }
                    } catch (AssumptionViolatedException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new AssumptionViolatedException("Could determine Java version", e);
                    }
                    base.evaluate();
                }
            };
        }
    }

    private static int javaMajorVersion() {
        String javaVersion = System.getProperty("java.version");
        if (javaVersion == null || javaVersion.trim().isEmpty()) {
            throw new IllegalStateException("JVM system property 'java.version' is undefined");
        }

        if (javaVersion.startsWith("1.8")) {
            return 8;
        }

        try {
            // from JUnit 5 JRE class
            // java.lang.Runtime.version() is a static method available on Java 9+
            // that returns an instance of java.lang.Runtime.Version which has the
            // following method: public int major()
            Method versionMethod = Runtime.class.getMethod("version");
            Object version = versionMethod.invoke(null);
            Method majorMethod = version.getClass().getMethod("major");
            int major = (int) majorMethod.invoke(version);
            if (major < 9) {
                throw new IllegalStateException("Invalid Java major version: " + major);
            }
            return major;
        } catch (Exception ex) {
            LOGGER.warn("Error while computing Java major version", ex);
        }
        throw new IllegalStateException("Could not determine Java major version");
    }
}
