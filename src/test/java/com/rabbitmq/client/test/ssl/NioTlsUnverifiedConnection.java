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

    public void openConnection()
        throws IOException, TimeoutException {
        try {
            connectionFactory.useSslProtocol();
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
            fail("Couldn't open TLS connection after 3 attemps");
        }

    }

    @Test
    public void connectionGetConsume() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        connection = basicGetBasicConsume(connection, "tls.nio.queue", latch);
        boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Message has not been received", messagesReceived);
    }

    @Test public void socketChannelConfigurator() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.useSslProtocol();
        NioParams nioParams = new NioParams();
        final AtomicBoolean sslEngineHasBeenCalled = new AtomicBoolean(false);
        nioParams.setSslEngineConfigurator(new SslEngineConfigurator() {
            @Override
            public void configure(SSLEngine sslEngine) throws IOException {
                sslEngineHasBeenCalled.set(true);
            }
        });

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

    private Connection basicGetBasicConsume(Connection connection, String queue, final CountDownLatch latch)
        throws IOException, TimeoutException {
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        channel.queuePurge(queue);

        channel.basicPublish("", queue, null, new byte[100 * 1000]);

        channel.basicConsume(queue, false, new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                latch.countDown();
            }
        });

        return connection;
    }

}
