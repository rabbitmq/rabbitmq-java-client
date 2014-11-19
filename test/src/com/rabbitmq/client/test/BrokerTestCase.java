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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2014 GoPivotal, Inc.  All rights reserved.
//


package com.rabbitmq.client.test;

import java.io.IOException;
import java.util.UUID;

import junit.framework.TestCase;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.impl.ShutdownNotifierComponent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.tools.Host;

public class BrokerTestCase extends TestCase {
    protected ConnectionFactory connectionFactory = newConnectionFactory();

    protected ConnectionFactory newConnectionFactory() {
        return new ConnectionFactory();
    }

    protected Connection connection;
    protected Channel channel;

    protected void setUp()
            throws IOException {
        openConnection();
        openChannel();

        createResources();
    }

    protected void tearDown()
            throws IOException {
        closeChannel();
        closeConnection();

        openConnection();
        openChannel();
        releaseResources();
        closeChannel();
        closeConnection();
    }

    /**
     * Should create any AMQP resources needed by the test. Will be
     * called by BrokerTestCase's implementation of setUp, after the
     * connection and channel have been opened.
     */
    protected void createResources()
            throws IOException {
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
            throws IOException {
        tearDown();
        bareRestart();
        setUp();
    }

    protected void bareRestart()
            throws IOException {
        Host.invokeMakeTarget("restart-app");
    }

    public void openConnection()
            throws IOException {
        if (connection == null) {
            connection = connectionFactory.newConnection();
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
            connection = null;
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

    protected void declareDurableTopicExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "topic", true);
    }

    protected void declareTransientTopicExchange(String x) throws IOException {
        channel.exchangeDeclare(x, "topic", false);
    }

    protected void deleteExchange(String x) throws IOException {
        channel.exchangeDelete(x);
    }

    protected void deleteQueue(String q) throws IOException {
        channel.queueDelete(q);
    }

    protected void clearAllResourceAlarms() throws IOException, InterruptedException {
        clearResourceAlarm("memory");
        clearResourceAlarm("disk");
    }

    protected void setResourceAlarm(String source) throws IOException, InterruptedException {
        Host.invokeMakeTarget("set-resource-alarm SOURCE=" + source);
    }

    protected void clearResourceAlarm(String source) throws IOException, InterruptedException {
        Host.invokeMakeTarget("clear-resource-alarm SOURCE=" + source);
    }

    protected void block() throws IOException, InterruptedException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.000000001");
        setResourceAlarm("disk");
    }

    protected void unblock() throws IOException, InterruptedException {
        Host.rabbitmqctl("set_vm_memory_high_watermark 0.4");
        clearResourceAlarm("disk");
    }

    protected String generateQueueName() {
        return "queue" + UUID.randomUUID().toString();
    }

    protected String generateExchangeName() {
        return "exchange" + UUID.randomUUID().toString();
    }
}
