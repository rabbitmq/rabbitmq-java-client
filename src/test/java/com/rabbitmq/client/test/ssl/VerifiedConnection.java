// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.impl.nio.NioParams;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.test.TestUtils;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
@EnabledForJreRange(min = JRE.JAVA_11)
public class VerifiedConnection extends UnverifiedConnection {

    @Test
    public void connectionGetConsumeProtocols() throws Exception {
        Collection<String> availableProtocols = TlsTestUtils.availableTlsProtocols();
        Collection<String> protocols = Stream.of("TLSv1.2", "TLSv1.3")
            .filter(availableProtocols::contains)
            .collect(Collectors.toList());
        for (String protocol : protocols) {
            SSLContext sslContextModel = SSLContext.getInstance(protocol);
            ConnectionFactory cf = TestUtils.connectionFactory();
            SSLContext sslContext = TlsTestUtils.verifiedSslContext(() -> sslContextModel);
            cf.useSslProtocol(sslContext);
            TlsTestUtils.maybeConfigureNetty(cf, sslContext);
            AtomicReference<Supplier<String[]>> protocolsSupplier = new AtomicReference<>();
            if (TestUtils.isNio()) {
                cf.useNio();
                NioParams nioParams = new NioParams();
                nioParams.setSslEngineConfigurator(
                    sslEngine -> protocolsSupplier.set(() -> sslEngine.getEnabledProtocols())
                );
                cf.setNioParams(nioParams);
            } else if (TestUtils.isSocket()) {
                cf.setSocketConfigurator(socket -> {
                    SSLSocket s = (SSLSocket) socket;
                    protocolsSupplier.set(s::getEnabledProtocols);
                });
            } else if (TestUtils.isNetty()) {
                cf.netty().channelCustomizer(ch -> {
                    SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
                    SSLEngine engine = sslHandler.engine();
                    protocolsSupplier.set(engine::getEnabledProtocols);
                });
            } else {
                throw new IllegalArgumentException();
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
