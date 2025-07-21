// Copyright (c) 2007-2023 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.tools.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static com.rabbitmq.client.test.TestUtils.currentVersion;
import static com.rabbitmq.client.test.TestUtils.versionCompare;
import static org.junit.jupiter.api.Assertions.*;

public class BrokerTestCase {

    private String brokerVersion;

    protected volatile TestInfo testInfo;

    protected ConnectionFactory connectionFactory = newConnectionFactory();

    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        if(TestUtils.USE_NIO) {
            connectionFactory.setNioParams(nioParams());
        }
        connectionFactory.setAutomaticRecoveryEnabled(isAutomaticRecoveryEnabled());
        return connectionFactory;
    }

    protected NioParams nioParams() {
        return new NioParams();
    }

    protected boolean isAutomaticRecoveryEnabled() {
        return true;
    }

    protected Connection connection;
    protected Channel channel;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException, TimeoutException {
        Assumptions.assumeTrue(shouldRun());
        this.testInfo = testInfo;
        openConnection();
        if (this.connection != null) {
            this.brokerVersion = currentVersion(this.connection.getServerProperties().get("version").toString());
        }
        openChannel();

        createResources();
    }

    @AfterEach public void tearDown(TestInfo testInfo)
            throws IOException, TimeoutException {
        if(shouldRun()) {
            closeChannel();
            closeConnection();

            openConnection();
            openChannel();
            releaseResources();
            closeChannel();
            closeConnection();
        }
    }

    /**
     * Whether to run the test or not.
     * Subclasses can check whether some broker features
     * are available or not, and choose not to run the test.
     * @return
     */
    protected boolean shouldRun() throws IOException {
        return true;
    }

    /**
     * Should create any AMQP resources needed by the test. Will be
     * called by BrokerTestCase's implementation of setUp, after the
     * connection and channel have been opened.
     */
    protected void createResources()
            throws IOException, TimeoutException {
    }

    /**
     * Should destroy any AMQP resources that were created by the
     * test. Will be called by BrokerTestCase's implementation of
     * tearDown, after the connection and channel have been closed and
     * reopened specifically for this method. After this method
     * completes, the connection and channel will be closed again.
     */
    protected void releaseResources()
            throws IOException {
    }

    protected void restart()
            throws IOException, TimeoutException {
        tearDown(this.testInfo);
        bareRestart();
        setUp(this.testInfo);
    }

    protected void bareRestart()
            throws IOException {
        Host.stopRabbitOnNode();
        Host.startRabbitOnNode();
    }

    public void openConnection()
            throws IOException, TimeoutException {
        if (connection == null) {
            connection = connectionFactory.newConnection(UUID.randomUUID().toString());
        }
    }

    public void closeConnection()
            throws IOException {
        if (connection != null) {
            connection.abort();
            connection = null;
        }
    }

    public void openChannel()
            throws IOException {
        channel = connection.createChannel();
    }

    public void closeChannel()
            throws IOException {
        if (channel != null) {
            channel.abort();
            channel = null;
        }
    }

    public void checkShutdownSignal(int expectedCode, IOException ioe) {
        ShutdownSignalException sse = (ShutdownSignalException) ioe.getCause();
        checkShutdownSignal(expectedCode, sse);
    }

    public void checkShutdownSignal(int expectedCode, ShutdownSignalException sse) {
        Method method = sse.getReason();
        channel = null;
        if (sse.isHardError()) {
            AMQP.Connection.Close closeMethod = (AMQP.Connection.Close) method;
            assertEquals(expectedCode, closeMethod.getReplyCode());
        } else {
            AMQP.Channel.Close closeMethod = (AMQP.Channel.Close) method;
            assertEquals(expectedCode, closeMethod.getReplyCode());
        }
    }

    public void expectError(int error) {
        try {
            channel.basicQos(0);
            fail("Expected channel error " + error);
        } catch (IOException ioe) {
            // If we get a channel close back when flushing it with the
            // synchronous basicQos above.
            checkShutdownSignal(error, ioe);
        } catch (AlreadyClosedException ace) {
            // If it has already closed of its own accord before we got there.
            checkShutdownSignal(error, ace);
        }
    }

    protected void assertDelivered(String q, int count) throws IOException {
        assertDelivered(q, count, false);
    }

    protected void assertDelivered(String q, int count, boolean redelivered) throws IOException {
        GetResponse r;
        for (int i = 0; i < count; i++) {
            r = basicGet(q);
            assertNotNull(r);
            assertEquals(redelivered, r.getEnvelope().isRedeliver());
        }
        assertNull(basicGet(q));
    }

    protected GetResponse basicGet(String q) throws IOException {
        return channel.basicGet(q, true);
    }

    protected void basicPublishPersistent(String q) throws IOException {
        basicPublishPersistent("persistent message".getBytes(), q);
    }

    protected void basicPublishPersistent(byte[] msg, String q) throws IOException {
        basicPublishPersistent(msg, "", q);
    }

    protected void basicPublishPersistent(String x, String routingKey) throws IOException {
        basicPublishPersistent("persistent message".getBytes(), x, routingKey);
    }


    protected void basicPublishPersistent(byte[] msg, String x, String routingKey) throws IOException {
        channel.basicPublish(x, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, msg);
    }

    protected void basicPublishVolatile(String q) throws IOException {
        basicPublishVolatile("not persistent message".getBytes(), q);
    }

    protected void basicPublishVolatile(byte[] msg, String q) throws IOException {
        basicPublishVolatile(msg, "", q);
    }

    protected void basicPublishVolatile(String x, String routingKey) throws IOException {
        basicPublishVolatile("not persistent message".getBytes(), x, routingKey);
    }

    protected void basicPublishVolatile(byte[] msg, String x, String routingKey) throws IOException {
        basicPublishVolatile(msg, x, routingKey, MessageProperties.TEXT_PLAIN);
    }

    public void basicPublishVolatile(byte[] msg, String x, String routingKey,
                                        AMQP.BasicProperties properties) throws IOException {
        channel.basicPublish(x, routingKey, properties, msg);
    }

    protected void declareAndBindDurableQueue(String q, String x, String r) throws IOException {
        declareDurableQueue(q);
        channel.queueBind(q, x, r);
    }

    protected void declareDurableDirectExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "direct", true);
    }

    protected void declareDurableQueue(String q) throws IOException {
        channel.queueDeclare(q, true, false, false, null);
    }

    protected void declareTransientQueue(String q) throws IOException {
        channel.queueDeclare(q, false, false, false, null);
    }

    protected void declareTransientQueue(String q, Map<String, Object> args) throws IOException {
        channel.queueDeclare(q, false, false, false, args);
    }

    protected void declareDurableTopicExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "topic", true);
    }

    protected void declareTransientTopicExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "topic", false);
    }

    protected void declareTransientFanoutExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "fanout", false);
    }

    protected void deleteExchange(String x) throws IOException {
        channel.exchangeDelete(x);
    }

    protected void deleteExchanges(String [] exchanges) throws IOException {
        if (exchanges != null) {
            for (String exchange : exchanges) {
                deleteExchange(exchange);
            }
        }
    }

    protected void deleteQueue(String q) throws IOException {
        channel.queueDelete(q);
    }

    protected void deleteQueues(String [] queues) throws IOException {
        if (queues != null) {
            for (String queue : queues) {
                deleteQueue(queue);
            }
        }
    }

    protected void clearAllResourceAlarms() throws IOException {
        clearResourceAlarm("memory");
        clearResourceAlarm("disk");
    }

    protected void setResourceAlarm(String source) throws IOException {
        Host.setResourceAlarm(source);
    }

    protected void clearResourceAlarm(String source) throws IOException {
        Host.clearResourceAlarm(source);
    }

    protected void block() throws IOException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.000000001");
        setResourceAlarm("disk");
    }

    protected void unblock() throws IOException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.4");
        clearResourceAlarm("disk");
    }

    protected String generateQueueName() {
        return name("queue", this.testInfo.getTestClass().get(),
            this.testInfo.getTestMethod().get().getName());
    }

    protected String generateExchangeName() {
        return name("exchange", this.testInfo.getTestClass().get(),
            this.testInfo.getTestMethod().get().getName());
    }

    private static String name(String prefix, Class<?> testClass, String testMethodName) {
        String uuid = UUID.randomUUID().toString();
        return String.format(
            "%s_%s_%s%s",
            prefix, testClass.getSimpleName(), testMethodName, uuid.substring(uuid.length() / 2));
    }

    protected boolean beforeMessageContainers() {
       return versionCompare(this.brokerVersion, "3.13.0") < 0;
    }



}
