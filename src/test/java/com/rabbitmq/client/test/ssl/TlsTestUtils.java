// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
// Inc. and/or its subsidiaries.
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

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.PemReader;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.Host;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.US_ASCII;

class TlsTestUtils {

  static final String[] PROTOCOLS = new String[] {"TLSv1.3", "TLSv1.2"};
  static TrustManager ALWAYS_TRUST_MANAGER = new AlwaysTrustManager();

  private TlsTestUtils() {}

  static SSLContext badVerifiedSslContext() throws Exception {
    return sslContext(trustManagerFactory(clientCertificate()));
  }

  static SSLContext verifiedSslContext() throws Exception {
    return sslContext(trustManagerFactory(caCertificate()));
  }

  static void maybeConfigureNetty(ConnectionFactory cf, SSLContext sslContext) {
    if (TestUtils.isNetty()) {
      cf.netty().sslContext(toSslContext(sslContext));
    }
  }

  static SslContext toSslContext(SSLContext jdkSslContext) {
    return new JdkSslContext(jdkSslContext, true, null, IdentityCipherSuiteFilter.INSTANCE,
        null, ClientAuth.NONE, null, false);
  }

  static void maybeConfigureNetty(ConnectionFactory cf, TrustManager trustManager) throws Exception {
    if (TestUtils.isNetty()) {
      cf.netty().sslContext(SslContextBuilder.forClient().trustManager(trustManager).build());
    }
  }

  static SSLContext verifiedSslContext(CallableSupplier<SSLContext> sslContextSupplier)
      throws Exception {
    return sslContext(sslContextSupplier, trustManagerFactory(caCertificate()));
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
      return Arrays.stream(protocols)
          .filter(p -> p.toLowerCase().startsWith("tls"))
          .collect(Collectors.toList());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  static SSLContext sslContext(TrustManagerFactory trustManagerFactory) throws Exception {
    return sslContext(() -> SSLContext.getInstance(PROTOCOLS[0]), trustManagerFactory);
  }

  static SSLContext sslContext(
      CallableSupplier<SSLContext> sslContextSupplier, TrustManagerFactory trustManagerFactory)
      throws Exception {
    SSLContext sslContext = sslContextSupplier.get();
    sslContext.init(
        null, trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(), null);
    return sslContext;
  }

  static TrustManagerFactory trustManagerFactory(Certificate certificate) throws Exception {
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    keyStore.setCertificateEntry("some-certificate", certificate);
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);
    return trustManagerFactory;
  }

  static X509Certificate caCertificate() throws Exception {
    return loadCertificate(caCertificateFile());
  }

  static String caCertificateFile() {
    return tlsArtefactPath(
        System.getProperty("ca.certificate", "./rabbitmq-configuration/tls/ca_certificate.pem"));
  }

  static X509Certificate clientCertificate() throws Exception {
    return loadCertificate(clientCertificateFile());
  }

  static String clientCertificateFile() {
    return tlsArtefactPath(
        System.getProperty(
            "client.certificate",
            "./rabbitmq-configuration/tls/client_" + hostname() + "_certificate.pem"));
  }

  static X509Certificate loadCertificate(String file) throws Exception
  {
    List<X509Certificate> certs = loadCertificateChain(file);
    if (certs.isEmpty()) {
      throw new CertificateException("No certificates found in file: " + file);
    }
    return certs.get(0);
    }

  /**
   * Load certificate chain from PEM file.
   * Supports files with multiple concatenated certificates and mixed content (cert + key).
   *
   * @param file Path to PEM file
   * @return List of certificates found in the file
   */
  static List<X509Certificate> loadCertificateChain(String file) throws Exception
  {
    String pemContent = new String(Files.readAllBytes(Paths.get(file)), US_ASCII);
    return PemReader.readCertificateChain(pemContent);
  }

  /**
   * Load KeyStore from combined PEM file containing both certificate(s) and private key.
   *
   * @param file Path to PEM file containing certificate chain and private key
   * @param keyPassword Password for encrypted private key (null if unencrypted)
   * @return KeyStore containing the certificate chain and private key
   */
  static KeyStore loadKeyStoreFromPem(String file, String keyPassword) throws Exception
  {
    String pemContent = new String(Files.readAllBytes(Paths.get(file)), US_ASCII);
    return PemReader.loadKeyStore(pemContent, pemContent, Optional.ofNullable(keyPassword));
  }

  /**
   * Load KeyStore from separate certificate and private key PEM files.
   *
   * @param certFile Path to PEM file containing certificate chain
   * @param keyFile Path to PEM file containing private key
   * @param keyPassword Password for encrypted private key (null if unencrypted)
   * @return KeyStore containing the certificate chain and private key
   */
  static KeyStore loadKeyStoreFromPem(String certFile, String keyFile, String keyPassword)
      throws Exception
  {
    String certContent = new String(Files.readAllBytes(Paths.get(certFile)), US_ASCII);
    String keyContent = new String(Files.readAllBytes(Paths.get(keyFile)), US_ASCII);
    return PemReader.loadKeyStore(certContent, keyContent, Optional.ofNullable(keyPassword));
  }

  private static String tlsArtefactPath(String in) {
    return in.replace("$(hostname)", hostname()).replace("$(hostname -s)", hostname());
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return Host.hostname();
    }
  }

  @FunctionalInterface
  interface CallableSupplier<T> {

    T get() throws Exception;
  }

  private static class AlwaysTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {

    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
