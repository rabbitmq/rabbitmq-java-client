// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.impl.nio.NioParams;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.test.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.slf4j.LoggerFactory;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
@EnabledForJreRange(min = JRE.JAVA_11)
public class VerifiedConnection extends UnverifiedConnection {

    public void openConnection()
            throws IOException, TimeoutException {
        try {
            SSLContext c = TlsTestUtils.verifiedSslContext();
            connectionFactory = TestUtils.connectionFactory();
            connectionFactory.useSslProtocol(c);
        } catch (Exception ex) {
            throw new IOException(ex);
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

    @Test
    public void connectionGetConsumeProtocols() throws Exception {
        Collection<String> availableProtocols = TlsTestUtils.availableTlsProtocols();
        Collection<String> protocols = Stream.of("TLSv1.2", "TLSv1.3")
            .filter(p -> availableProtocols.contains(p))
            .collect(Collectors.toList());
        for (String protocol : protocols) {
            SSLContext sslContext = SSLContext.getInstance(protocol);
            ConnectionFactory cf = TestUtils.connectionFactory();
            cf.useSslProtocol(TlsTestUtils.verifiedSslContext(() -> sslContext));
            AtomicReference<Supplier<String[]>> protocolsSupplier = new AtomicReference<>();
            if (TestUtils.USE_NIO) {
                cf.useNio();
                cf.setNioParams(new NioParams()
                    .setSslEngineConfigurator(sslEngine -> {
                        protocolsSupplier.set(() -> sslEngine.getEnabledProtocols());
                    }));
            } else {
                cf.setSocketConfigurator(socket -> {
                    SSLSocket s = (SSLSocket) socket;
                    protocolsSupplier.set(() -> s.getEnabledProtocols());
                });
            }
            try (Connection c = cf.newConnection()) {
                CountDownLatch latch = new CountDownLatch(1);
                TestUtils.basicGetBasicConsume(c, VerifiedConnection.class.getName(), latch, 100);
                boolean messagesReceived = latch.await(5, TimeUnit.SECONDS);
                assertTrue(messagesReceived, "Message has not been received");
                assertThat(protocolsSupplier.get()).isNotNull();
                assertThat(protocolsSupplier.get().get()).contains(protocol);
            }
        }
    }

}
