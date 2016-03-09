//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2016 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.ssl;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import com.rabbitmq.client.ConnectionFactory;

/**
 * Test for bug 19356 - SSL Support in rabbitmq
 *
 */
public class BadVerifiedConnection extends UnverifiedConnection {
    public void openConnection()
            throws IOException, TimeoutException {
        try {
            String keystorePath = System.getProperty("keystore.empty.path");
            assertNotNull(keystorePath);
            String keystorePasswd = System.getProperty("keystore.passwd");
            assertNotNull(keystorePasswd);
            char [] keystorePassword = keystorePasswd.toCharArray();

            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load(new FileInputStream(keystorePath), keystorePassword);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(tks);

            String p12Path = System.getProperty("p12.path");
            assertNotNull(p12Path);
            String p12Passwd = System.getProperty("p12.passwd");
            assertNotNull(p12Passwd);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            char [] p12Password = p12Passwd.toCharArray();
            ks.load(new FileInputStream(p12Path), p12Password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, p12Password);
            
            SSLContext c = getSSLContext();
            c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            connectionFactory = new ConnectionFactory();
            connectionFactory.useSslProtocol(c);
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

        if (connection == null) {
            try {
                connection = connectionFactory.newConnection();
                fail();
            } catch (SSLHandshakeException ignored) {
            } catch (IOException e) {
                fail();
            }
        }
    }

    public void openChannel() {}
    public void testSSL() {}
}
