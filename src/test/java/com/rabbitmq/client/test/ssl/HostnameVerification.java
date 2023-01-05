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

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.test.TestUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_11)
public class HostnameVerification {

    static SSLContext sslContext;

    public static Object[] data() {
        return new Object[] {
            blockingIo(enableHostnameVerification()),
            nio(enableHostnameVerification()),
        };
    }

    private static Consumer<ConnectionFactory> blockingIo(final Consumer<ConnectionFactory> customizer) {
        return connectionFactory -> {
            connectionFactory.useBlockingIo();
            customizer.accept(connectionFactory);
        };
    }

    private static Consumer<ConnectionFactory> nio(final Consumer<ConnectionFactory> customizer) {
        return connectionFactory -> {
            connectionFactory.useNio();
            customizer.accept(connectionFactory);
        };
    }

    private static Consumer<ConnectionFactory> enableHostnameVerification() {
        return connectionFactory -> connectionFactory.enableHostnameVerification();
    }

    @BeforeAll
    public static void initCrypto() throws Exception {
        sslContext = TlsTestUtils.verifiedSslContext();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void hostnameVerificationFailsBecauseCertificateNotIssuedForLoopbackInterface(Consumer<ConnectionFactory> customizer) throws Exception {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        customizer.accept(connectionFactory);
        Assertions.assertThatThrownBy(() -> connectionFactory.newConnection(
            () -> singletonList(new Address("127.0.0.1", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT))))
            .isInstanceOf(SSLHandshakeException.class)
            .as("The server certificate isn't issued for 127.0.0.1, the TLS handshake should have failed");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void hostnameVerificationSucceeds(Consumer<ConnectionFactory> customizer) throws Exception {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        customizer.accept(connectionFactory);
        try (Connection conn = connectionFactory.newConnection(
            () -> singletonList(new Address("localhost", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT)))) {
            assertTrue(conn.isOpen());
        }
    }
}
