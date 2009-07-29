//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
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
        throws IOException
    {
        connectionFactory = new ConnectionFactory();
        try {
            String keystorePath = System.getProperty("keystore.empty.path");
            assertNotNull(keystorePath);
            String keystorePasswd = System.getProperty("keystore.phrase");
            assertNotNull(keystorePasswd);
            char [] passphrase = keystorePasswd.toCharArray();

            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keystorePath), passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext c = SSLContext.getInstance("SSLv3");
            c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

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
                connection = connectionFactory.newConnection("localhost", 5671);
                fail();
            } catch (SSLHandshakeException e) {
            } catch (IOException e) {
                fail();
            }
        }
    }

    public void openChannel() {}
    public void testSSL() {}
}
