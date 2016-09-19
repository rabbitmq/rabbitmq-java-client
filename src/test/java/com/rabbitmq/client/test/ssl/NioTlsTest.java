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
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NioTlsTest {

    @Test
    public void connectionGetConsume() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setNio(true);
        connectionFactory.useSslProtocol();

        CountDownLatch latch = new CountDownLatch(1);
        Connection connection = null;
        try {
            connection = basicGetBasicConsume(connectionFactory, "tls.nio.queue", latch);
            boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Message has not been received", messagesReceived);
        } finally {
            safeClose(connection);
        }
    }

    private Connection basicGetBasicConsume(ConnectionFactory connectionFactory, String queue, final CountDownLatch latch)
        throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
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
