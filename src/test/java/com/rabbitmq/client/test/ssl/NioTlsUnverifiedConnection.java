// Copyright (c) 2007-2021 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.nio.NioParams;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.rabbitmq.client.test.TestUtils.basicGetBasicConsume;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    public void connectionGetConsumeProtocols() throws Exception {
        String [] protocols = new String[] {"TLSv1.2", "TLSv1.3"};
        for (String protocol : protocols) {
            SSLContext sslContext = SSLContext.getInstance(protocol);
            sslContext.init(null, new TrustManager[] {new TrustEverythingTrustManager()}, null);
            ConnectionFactory cf = TestUtils.connectionFactory();
            cf.useSslProtocol(sslContext);
            cf.useNio();
            AtomicReference<SSLEngine> engine = new AtomicReference<>();
            cf.setNioParams(new NioParams()
                    .setSslEngineConfigurator(sslEngine -> engine.set(sslEngine)));
            try (Connection c = cf.newConnection()) {
                CountDownLatch latch = new CountDownLatch(1);
                basicGetBasicConsume(c, QUEUE, latch, 100);
                boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
                assertTrue("Message has not been received", messagesReceived);
                assertThat(engine.get()).isNotNull();
                assertThat(engine.get().getEnabledProtocols()).contains(protocol);
            }
        }
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

}
