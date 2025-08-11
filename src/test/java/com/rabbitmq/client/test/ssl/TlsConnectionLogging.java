// Copyright (c) 2019-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.TlsUtils;
import com.rabbitmq.client.test.TestUtils;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TlsConnectionLogging {

    public static Object[] certificateInfoAreProperlyExtracted() {
        return new Object[]{blockingIo(), netty()};
    }

    public static Function<ConnectionFactory, Supplier<SSLSession>> blockingIo() {
        return connectionFactory -> {
            connectionFactory.useBlockingIo();
            AtomicReference<SSLSocket> socketCaptor = new AtomicReference<>();
            connectionFactory.setSocketConfigurator(socket -> socketCaptor.set((SSLSocket) socket));
            return () -> socketCaptor.get().getSession();
        };
    }

    public static Function<ConnectionFactory, Supplier<SSLSession>> netty() {
        return connectionFactory -> {
            AtomicReference<SSLEngine> sslEngineCaptor = new AtomicReference<>();
            try {
                connectionFactory.netty()
                    .channelCustomizer(ch -> {
                        SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
                        sslEngineCaptor.set(sslHandler.engine());
                    })
                    .sslContext(SslContextBuilder.forClient()
                        .trustManager(TlsTestUtils.ALWAYS_TRUST_MANAGER)
                        .build());
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
            return () -> sslEngineCaptor.get().getSession();
        };
    }

    @ParameterizedTest
    @MethodSource
    public void certificateInfoAreProperlyExtracted(Function<ConnectionFactory, Supplier<SSLSession>> configurer) throws Exception {
        SSLContext sslContext = TlsTestUtils.getSSLContext();
        sslContext.init(null, new TrustManager[]{TlsTestUtils.ALWAYS_TRUST_MANAGER}, null);
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        Supplier<SSLSession> sslSessionSupplier = configurer.apply(connectionFactory);
        try (Connection ignored = connectionFactory.newConnection()) {
            SSLSession session = sslSessionSupplier.get();
            assertNotNull(session);
            String info = TlsUtils.peerCertificateInfo(session.getPeerCertificates()[0], "some prefix");
            Assertions.assertThat(info).contains("some prefix")
                    .contains("CN=")
                    .contains("X.509 usage extensions")
                    .contains("KeyUsage");

        }
    }

}
