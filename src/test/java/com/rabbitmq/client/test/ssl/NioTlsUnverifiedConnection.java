// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.test.BrokerTestCase;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
public class NioTlsUnverifiedConnection extends BrokerTestCase {

    public static final String QUEUE = "tls.nio.queue";

    public void openConnection()
        throws IOException, TimeoutException {
        try {
            connectionFactory.useSslProtocol();
            connectionFactory.useNio();
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }

        int attempt = 0;
        while(attempt < 3) {
            try {
                connection = connectionFactory.newConnection();
                break;
            } catch(Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Error when opening TLS connection");
                attempt++;
            }
        }
        if(connection == null) {
            fail("Couldn't open TLS connection after 3 attempts");
        }

    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(QUEUE);
    }

    @Test
    public void connectionGetConsume() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        basicGetBasicConsume(connection, QUEUE, latch, 100 * 1000);
        boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Message has not been received", messagesReceived);
    }

    @Test public void socketChannelConfigurator() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.useSslProtocol();
        NioParams nioParams = new NioParams();
        final AtomicBoolean sslEngineHasBeenCalled = new AtomicBoolean(false);
        nioParams.setSslEngineConfigurator(sslEngine -> sslEngineHasBeenCalled.set(true));

        connectionFactory.setNioParams(nioParams);

        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            assertTrue("The SSL engine configurator should have called", sslEngineHasBeenCalled.get());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test public void messageSize() throws Exception {
        int[] sizes = new int[]{100, 1000, 10 * 1000, 1 * 1000 * 1000, 5 * 1000 * 1000};
        for (int size : sizes) {
            sendAndVerifyMessage(size);
        }
    }

    // The purpose of this test is to put some stress on client TLS layer (SslEngineByteBufferInputStream to be specific)
    // in an attempt to trigger condition described in https://github.com/rabbitmq/rabbitmq-java-client/issues/317
    // Unfortunately it is not guaranteed to be reproducible
    @Test public void largeMessagesTlsTraffic() throws Exception {
        for (int i = 0; i < 50; i++) {
            sendAndVerifyMessage(76390);
        }
    }

    private void sendAndVerifyMessage(int size) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        boolean messageReceived = basicGetBasicConsume(connection, QUEUE, latch, size);
        assertTrue("Message has not been received", messageReceived);
    }

    private boolean basicGetBasicConsume(Connection connection, String queue, final CountDownLatch latch, int msgSize)
        throws Exception {
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
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

}
