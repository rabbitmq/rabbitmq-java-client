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

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.test.TestUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.function.Consumer;

import static com.rabbitmq.client.test.TestUtils.getSSLContext;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class HostnameVerification {

    static SSLContext sslContext;
    @Parameterized.Parameter
    public Consumer<ConnectionFactory> customizer;

    @Parameterized.Parameters
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

    @BeforeClass
    public static void initCrypto() throws Exception {
        String keystorePath = System.getProperty("test-keystore.ca");
        assertNotNull(keystorePath);
        String keystorePasswd = System.getProperty("test-keystore.password");
        assertNotNull(keystorePasswd);
        char[] keystorePassword = keystorePasswd.toCharArray();

        KeyStore tks = KeyStore.getInstance("JKS");
        tks.load(new FileInputStream(keystorePath), keystorePassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(tks);

        String p12Path = System.getProperty("test-client-cert.path");
        assertNotNull(p12Path);
        String p12Passwd = System.getProperty("test-client-cert.password");
        assertNotNull(p12Passwd);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        char[] p12Password = p12Passwd.toCharArray();
        ks.load(new FileInputStream(p12Path), p12Password);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, p12Password);

        sslContext = getSSLContext();
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    }

    @Test(expected = SSLHandshakeException.class)
    public void hostnameVerificationFailsBecauseCertificateNotIssuedForLoopbackInterface() throws Exception {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        customizer.accept(connectionFactory);
        connectionFactory.newConnection(
            () -> singletonList(new Address("127.0.0.1", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT)));
        fail("The server certificate isn't issued for 127.0.0.1, the TLS handshake should have failed");
    }

    @Test
    public void hostnameVerificationSucceeds() throws Exception {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        customizer.accept(connectionFactory);
        try (Connection conn = connectionFactory.newConnection(
            () -> singletonList(new Address("localhost", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT)))) {
            assertTrue(conn.isOpen());
        }
    }
}
