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
import com.rabbitmq.client.test.TestUtils;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class HostnameVerification extends UnverifiedConnection {

    public void openConnection()
        throws IOException, TimeoutException {
        try {
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

            SSLContext c = getSSLContext();
            c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            c.init(null, tmf.getTrustManagers(), null);

            connectionFactory = TestUtils.connectionFactory();
            connectionFactory.useSslProtocol(c);
            connectionFactory.enableHostnameVerification();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex.toString());
        } catch (KeyManagementException ex) {
            throw new IOException(ex.toString());
        } catch (KeyStoreException ex) {
            throw new IOException(ex.toString());
        } catch (CertificateException ex) {
            throw new IOException(ex.toString());
        } catch (UnrecoverableKeyException ex) {
            throw new IOException(ex.toString());
        }

        try {
            connection = connectionFactory.newConnection(
                new AddressResolver() {
                    @Override
                    public List<Address> getAddresses() throws IOException {
                        return singletonList(new Address("127.0.0.1", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT));
                    }
                });
            fail("The server certificate isn't issued for 127.0.0.1, the TLS handshake should have failed");
        } catch (SSLHandshakeException ignored) {
        } catch (IOException e) {
            fail();
        }
    }

    public void openChannel() {
    }

    @Test
    public void sSL() {
    }
}
