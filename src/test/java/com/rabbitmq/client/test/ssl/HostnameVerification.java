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
import com.rabbitmq.client.AddressResolver;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionPostProcessors;
import com.rabbitmq.client.test.TestUtils;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.bouncycastle.est.jcajce.JsseDefaultHostnameAuthorizer;
import org.bouncycastle.est.jcajce.JsseHostnameAuthorizer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;

import static com.rabbitmq.client.test.TestUtils.getSSLContext;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class HostnameVerification {

    static SSLContext sslContext;
    @Parameterized.Parameter
    public ConnectionFactoryCustomizer customizer;

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[] {
            blockingIo(enableOnConnectionFactory()), nio(enableOnConnectionFactory()),
            blockingIo(useCommonsHttpHostnameVerifierInConnectionPostProcessor()), nio(useCommonsHttpHostnameVerifierInConnectionPostProcessor()),
            blockingIo(useCommonsHttpHostnameVerifierOnConnectionFactory()), nio(useCommonsHttpHostnameVerifierOnConnectionFactory()),
            blockingIo(bouncyCastleHostnameVerifierAdapter()), nio(bouncyCastleHostnameVerifierAdapter())
        };
    }

    private static ConnectionFactoryCustomizer blockingIo(final ConnectionFactoryCustomizer customizer) {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                connectionFactory.useBlockingIo();
                customizer.customize(connectionFactory);
            }
        };
    }

    private static ConnectionFactoryCustomizer nio(final ConnectionFactoryCustomizer customizer) {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                connectionFactory.useNio();
                customizer.customize(connectionFactory);
            }
        };
    }

    // a simple adapter for Bouncy Castle
    private static ConnectionFactoryCustomizer bouncyCastleHostnameVerifierAdapter() {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                final JsseHostnameAuthorizer hostnameAuthorizer = new JsseDefaultHostnameAuthorizer(Collections.EMPTY_SET);
                connectionFactory.enableHostnameVerification(new HostnameVerifier() {

                    @Override
                    public boolean verify(String hostname, SSLSession sslSession) {
                        try {
                            return hostnameAuthorizer.verified(hostname, sslSession);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        };
    }

    private static ConnectionFactoryCustomizer useCommonsHttpHostnameVerifierOnConnectionFactory() {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                connectionFactory.enableHostnameVerification(new DefaultHostnameVerifier());
            }
        };
    }

    private static ConnectionFactoryCustomizer useCommonsHttpHostnameVerifierInConnectionPostProcessor() {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                connectionFactory.setConnectionPostProcessor(ConnectionPostProcessors.builder()
                    .enableHostnameVerification(new DefaultHostnameVerifier())
                    .build());
            }
        };
    }

    private static ConnectionFactoryCustomizer enableOnConnectionFactory() {
        return new ConnectionFactoryCustomizer() {

            @Override
            public void customize(ConnectionFactory connectionFactory) {
                connectionFactory.enableHostnameVerification();
            }
        };
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
        customizer.customize(connectionFactory);
        connectionFactory.newConnection(
            new AddressResolver() {

                @Override
                public List<Address> getAddresses() {
                    return singletonList(new Address("127.0.0.1", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT));
                }
            });
        fail("The server certificate isn't issued for 127.0.0.1, the TLS handshake should have failed");
    }

    interface ConnectionFactoryCustomizer {

        void customize(ConnectionFactory connectionFactory);
    }
}
