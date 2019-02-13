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

package com.rabbitmq.client.test;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SslContextFactory;
import com.rabbitmq.client.TrustEverythingTrustManager;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.fail;

/**
 *
 */
public class SslContextFactoryTest {

    @Test public void setSslContextFactory() throws Exception {
        doTestSetSslContextFactory(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useBlockingIo();
            connectionFactory.setAutomaticRecoveryEnabled(true);
            return connectionFactory;
        });
        doTestSetSslContextFactory(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useNio();
            connectionFactory.setAutomaticRecoveryEnabled(true);
            return connectionFactory;
        });
        doTestSetSslContextFactory(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useBlockingIo();
            connectionFactory.setAutomaticRecoveryEnabled(false);
            return connectionFactory;
        });
        doTestSetSslContextFactory(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useNio();
            connectionFactory.setAutomaticRecoveryEnabled(false);
            return connectionFactory;
        });
    }

    private void doTestSetSslContextFactory(Supplier<ConnectionFactory> supplier) throws Exception {
        ConnectionFactory connectionFactory = supplier.get();
        SslContextFactory sslContextFactory = sslContextFactory();
        connectionFactory.setSslContextFactory(sslContextFactory);

        Connection connection = connectionFactory.newConnection("connection01");
        TestUtils.close(connection);
        try {
            connectionFactory.newConnection("connection02");
            fail("The SSL context of this client should not trust the server");
        } catch (SSLHandshakeException e) {
            // OK
        }
    }

    @Test public void socketFactoryTakesPrecedenceOverSslContextFactoryWithBlockingIo() throws Exception {
        doTestSocketFactoryTakesPrecedenceOverSslContextFactoryWithBlockingIo(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useBlockingIo();
            connectionFactory.setAutomaticRecoveryEnabled(true);
            return connectionFactory;
        });
        doTestSocketFactoryTakesPrecedenceOverSslContextFactoryWithBlockingIo(() -> {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.useBlockingIo();
            connectionFactory.setAutomaticRecoveryEnabled(false);
            return connectionFactory;
        });
    }

    private void doTestSocketFactoryTakesPrecedenceOverSslContextFactoryWithBlockingIo(
                Supplier<ConnectionFactory> supplier
            ) throws Exception {
        ConnectionFactory connectionFactory = supplier.get();
        connectionFactory.useBlockingIo();
        SslContextFactory sslContextFactory = sslContextFactory();
        connectionFactory.setSslContextFactory(sslContextFactory);

        SSLContext contextAcceptAll = sslContextFactory.create("connection01");
        connectionFactory.setSocketFactory(contextAcceptAll.getSocketFactory());

        Connection connection = connectionFactory.newConnection("connection01");
        TestUtils.close(connection);
        connection = connectionFactory.newConnection("connection02");
        TestUtils.close(connection);
    }

    private SslContextFactory sslContextFactory() throws Exception {
        SSLContext contextAcceptAll = SSLContext.getInstance(tlsProtocol());
        contextAcceptAll.init(null, new TrustManager[] { new TrustEverythingTrustManager() }, null);

        SSLContext contextRejectAll = SSLContext.getInstance(tlsProtocol());
        contextRejectAll.init(null, new TrustManager[] { new TrustNothingTrustManager() }, null);

        Map<String, SSLContext> sslContexts = new HashMap<>();
        sslContexts.put("connection01", contextAcceptAll);
        sslContexts.put("connection02", contextRejectAll);

        SslContextFactory sslContextFactory = name -> sslContexts.get(name);
        return sslContextFactory;
    }

    private String tlsProtocol() throws NoSuchAlgorithmException {
        return ConnectionFactory.computeDefaultTlsProtocol(SSLContext.getDefault().getSupportedSSLParameters().getProtocols());
    }

    private static class TrustNothingTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            throw new CertificateException("Doesn't trust any server");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
