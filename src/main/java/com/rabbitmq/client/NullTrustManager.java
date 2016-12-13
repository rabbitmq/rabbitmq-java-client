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


package com.rabbitmq.client;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Convenience class providing a default implementation of javax.net.ssl.X509TrustManager.
 * Instead doing nothing with the certs delegate to Java's default TrustManager
 * in case if the server is self-signed we have 2 options
 * 1. create a new trust store and pass it to connectionfactory
 * 2. import the root-ca into $JAVA_HOME/jre/lib/security/cacerts
 */
public class NullTrustManager implements X509TrustManager {
    private final List<X509TrustManager> trustManagers;

    public NullTrustManager(List<X509TrustManager> trustManagers) {
        this.trustManagers = Collections.unmodifiableList(trustManagers);
    }

    public NullTrustManager() {
        this((KeyStore)null);
    }

    public NullTrustManager(KeyStore keystore) {
        List<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();
        x509TrustManagers.add(getDefaultTrustManager());
        x509TrustManagers.add(getTrustManager(keystore));

        this.trustManagers = Collections.unmodifiableList(x509TrustManagers);

    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkClientTrusted(chain, authType);
                return; // someone trusts them. success!
            } catch (CertificateException e) {
                // maybe someone else will trust them
            }
        }
        throw new CertificateException("None of the TrustManagers trust this certificate chain");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkServerTrusted(chain, authType);
                return; // someone trusts them. success!
            } catch (CertificateException e) {
                // maybe someone else will trust them
            }
        }
        throw new CertificateException("None of the TrustManagers trust this certificate chain");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        List<X509Certificate> certificates = new ArrayList<X509Certificate>();
        for (X509TrustManager trustManager : trustManagers) {
            for (X509Certificate cert : trustManager.getAcceptedIssuers()) {
                certificates.add(cert);
            }
        }
        return (X509Certificate[])certificates.toArray();
    }

    public static TrustManager[] getTrustManagers(KeyStore keyStore) {

        return new TrustManager[] { new NullTrustManager(keyStore) };

    }

    public static X509TrustManager getDefaultTrustManager() {

        return getTrustManager(null);

    }

    public static X509TrustManager getTrustManager(KeyStore keystore) {

        return getTrustManager(TrustManagerFactory.getDefaultAlgorithm(), keystore);

    }

    public static X509TrustManager getTrustManager(String algorithm, KeyStore keystore) {

        TrustManagerFactory factory;
        X509TrustManager x509TrustManager = null;

        try {
            factory = TrustManagerFactory.getInstance(algorithm);
            factory.init(keystore);
            for (TrustManager trustManager : factory.getTrustManagers()) {

                if (trustManager instanceof X509TrustManager) {
                     x509TrustManager = (X509TrustManager)trustManager;
                    break;
                }
            }

        } catch (NoSuchAlgorithmException  e) {
            e.printStackTrace();
        }catch ( KeyStoreException e){
            e.printStackTrace();
        }

        return x509TrustManager;

    }

}
