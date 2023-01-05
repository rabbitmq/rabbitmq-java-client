// Copyright (c) 2021 VMware, Inc. or its affiliates.  All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

class TlsTestUtils {

  private TlsTestUtils() {}

  static SSLContext badVerifiedSslContext() throws Exception {
    return verifiedSslContext(() -> getSSLContext(), emptyKeystoreCa());
  }

  static SSLContext verifiedSslContext() throws Exception {
    return verifiedSslContext(() -> getSSLContext(), keystoreCa());
  }

  static SSLContext verifiedSslContext(CallableSupplier<SSLContext> sslContextSupplier) throws Exception {
    return verifiedSslContext(sslContextSupplier, keystoreCa());
  }

  static SSLContext verifiedSslContext(CallableSupplier<SSLContext> sslContextSupplier, String keystorePath) throws Exception {
    // for local testing, run ./mvnw test-compile -Dtest-tls-certs.dir=/tmp/tls-gen/basic
    // (generates the Java keystores)
    assertNotNull(keystorePath);
    String keystorePasswd = keystorePassword();
    assertNotNull(keystorePasswd);
    char [] keystorePassword = keystorePasswd.toCharArray();

    KeyStore tks = KeyStore.getInstance("JKS");
    tks.load(new FileInputStream(keystorePath), keystorePassword);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(tks);

    String p12Path = clientCertPath();
    assertNotNull(p12Path);
    String p12Passwd = clientCertPassword();
    assertNotNull(p12Passwd);
    KeyStore ks = KeyStore.getInstance("PKCS12");
    char [] p12Password = p12Passwd.toCharArray();
    ks.load(new FileInputStream(p12Path), p12Password);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, p12Password);

    SSLContext c = sslContextSupplier.get();
    c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    return c;
  }

  static String keystoreCa() {
    return System.getProperty("test-keystore.ca", "./target/ca.keystore");
  }

  static String emptyKeystoreCa() {
    return System.getProperty("test-keystore.empty", "./target/empty.keystore");
  }

  static String keystorePassword() {
    return System.getProperty("test-keystore.password", "bunnies");
  }

  static String clientCertPath() {
    return System.getProperty("test-client-cert.path", "/tmp/tls-gen/basic/client/keycert.p12");
  }

  static String clientCertPassword() {
    return System.getProperty("test-client-cert.password", "");
  }

  public static SSLContext getSSLContext() throws NoSuchAlgorithmException {
    SSLContext c;

    // pick the first protocol available, preferring TLSv1.2, then TLSv1,
    // falling back to SSLv3 if running on an ancient/crippled JDK
    for (String proto : Arrays.asList("TLSv1.3", "TLSv1.2", "TLSv1", "SSLv3")) {
      try {
        c = SSLContext.getInstance(proto);
        return c;
      } catch (NoSuchAlgorithmException x) {
        // keep trying
      }
    }
    throw new NoSuchAlgorithmException();
  }

  static Collection<String> availableTlsProtocols() {
    try {
      String[] protocols = SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
      return Arrays.stream(protocols).filter(p -> p.toLowerCase().startsWith("tls")).collect(
          Collectors.toList());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  interface CallableSupplier <T> {

    T get() throws Exception;
  }
}
