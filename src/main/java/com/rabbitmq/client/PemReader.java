// Copyright (c) 2017-2025 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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
package com.rabbitmq.client;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Base64.getMimeDecoder;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.crypto.Cipher.DECRYPT_MODE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Note: this class is copied from org.apache.zookeeper.util.PemReader (see
 * https://github.com/apache/zookeeper/blob/master/zookeeper-server/src/main/java/org/apache/zookeeper/util/PemReader.java)
 * under the same Apache License 2.0 (ASL).
 *
 * <p>The following modifications have been made to the original source code:
 *
 * <ul>
 *   <li>removed methods around loading trustStores.
 *   <li>updated the regular expressions to avoid exponential backtracking.
 * </ul>
 */
public final class PemReader {

  private static final Pattern CERT_PATTERN =
      Pattern.compile(
          "-+BEGIN\\s+.*CERTIFICATE[^-]*-+\\s*" // Header
              + "([a-z0-9+/=\\s]+)" // Base64 text
              + "-+END\\s+.*CERTIFICATE[^-]*-+", // Footer
          CASE_INSENSITIVE);

  private static final Pattern PRIVATE_KEY_PATTERN =
      Pattern.compile(
          "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+\\s*" // Header
              + "([a-z0-9+/=\\s]+)" // Base64 text
              + "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", // Footer
          CASE_INSENSITIVE);

  private PemReader() {}

  /**
   * Loads a KeyStore from PEM file contents.
   *
   * <p>This method reads a private key and certificate chain from PEM-formatted content and stores
   * them in a JKS KeyStore. The certificate file must contain at least one certificate.
   *
   * @param certificateChainContents the PEM-formatted content containing the certificate chain
   * @param privateKeyContents the PEM-formatted content containing the private key
   * @param keyPassword optional password for the private key; if the key is encrypted, this
   *     password will be used to decrypt it
   * @return a KeyStore containing the private key and certificate chain
   * @throws IOException if an I/O error occurs while reading the PEM content
   * @throws GeneralSecurityException if a security-related error occurs, such as:
   *     <ul>
   *       <li>the private key cannot be loaded
   *       <li>the certificate chain is empty
   *       <li>the KeyStore cannot be initialized
   *     </ul>
   *
   * @throws CertificateException if the certificate file does not contain any certificates
   */
  public static KeyStore loadKeyStore(
      String certificateChainContents, String privateKeyContents, Optional<String> keyPassword)
      throws IOException, GeneralSecurityException {
    PrivateKey key = loadPrivateKey(privateKeyContents, keyPassword);

    List<X509Certificate> certificateChain = readCertificateChain(certificateChainContents);
    if (certificateChain.isEmpty()) {
      throw new CertificateException(
          "Certificate file does not contain any certificates: " + certificateChainContents);
    }

    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, null);
    keyStore.setKeyEntry(
        "key",
        key,
        keyPassword.orElse("").toCharArray(),
        certificateChain.toArray(new Certificate[0]));
    return keyStore;
  }

  /**
   * Reads a chain of X.509 certificates from PEM-formatted content.
   *
   * <p>This method extracts all certificates found in the provided PEM content. Certificates are
   * identified by BEGIN CERTIFICATE and END CERTIFICATE markers. The certificates are returned in
   * the order they appear in the input.
   *
   * @param certificateChainContents the PEM-formatted content containing one or more certificates
   * @return a list of X.509 certificates extracted from the PEM content; may be empty if no
   *     certificates are found
   * @throws CertificateException if any certificate cannot be parsed or generated
   */
  public static List<X509Certificate> readCertificateChain(String certificateChainContents)
      throws CertificateException {
    Matcher matcher = CERT_PATTERN.matcher(certificateChainContents);
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certificates = new ArrayList<>();

    int start = 0;
    while (matcher.find(start)) {
      byte[] buffer = base64Decode(matcher.group(1));
      certificates.add(
          (X509Certificate)
              certificateFactory.generateCertificate(new ByteArrayInputStream(buffer)));
      start = matcher.end();
    }

    return certificates;
  }

  /**
   * Loads a private key from PEM-formatted content.
   *
   * <p>This method supports both encrypted and unencrypted private keys. The key must be in PKCS#8
   * format. To convert a key to PKCS#8 format, use: {@code openssl pkcs8 -topk8 ...}
   *
   * <p>The method attempts to load the key using RSA, EC, and DSA algorithms in that order.
   *
   * @param privateKey the PEM-formatted content containing the private key
   * @param keyPassword optional password for decrypting an encrypted private key; if empty, the key
   *     is assumed to be unencrypted
   * @return the loaded private key
   * @throws IOException if an I/O error occurs while reading the key
   * @throws GeneralSecurityException if a security-related error occurs, such as:
   *     <ul>
   *       <li>no private key is found in the content
   *       <li>the key format is invalid
   *       <li>the key cannot be decrypted with the provided password
   *       <li>the key algorithm is not supported (RSA, EC, or DSA)
   *     </ul>
   */
  public static PrivateKey loadPrivateKey(String privateKey, Optional<String> keyPassword)
      throws IOException, GeneralSecurityException {
    Matcher matcher = PRIVATE_KEY_PATTERN.matcher(privateKey);
    if (!matcher.find()) {
      throw new KeyStoreException("did not find a private key");
    }
    byte[] encodedKey = base64Decode(matcher.group(1));

    PKCS8EncodedKeySpec encodedKeySpec;
    if (keyPassword.isPresent()) {
      EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(encodedKey);
      SecretKeyFactory keyFactory =
          SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
      SecretKey secretKey =
          keyFactory.generateSecret(new PBEKeySpec(keyPassword.get().toCharArray()));

      Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
      cipher.init(DECRYPT_MODE, secretKey, encryptedPrivateKeyInfo.getAlgParameters());

      encodedKeySpec = encryptedPrivateKeyInfo.getKeySpec(cipher);
    } else {
      encodedKeySpec = new PKCS8EncodedKeySpec(encodedKey);
    }

    // this code requires a key in PKCS8 format which is not the default openssl format
    // to convert to the PKCS8 format you use : openssl pkcs8 -topk8 ...
    List<String> attemptedAlgorithms = new ArrayList<>();
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(encodedKeySpec);
    } catch (InvalidKeySpecException e) {
      attemptedAlgorithms.add("RSA: " + e.getMessage());
    }

    try {
      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      return keyFactory.generatePrivate(encodedKeySpec);
    } catch (InvalidKeySpecException e) {
      attemptedAlgorithms.add("RSA: " + e.getMessage());
    }

    try {
      return KeyFactory.getInstance("DSA").generatePrivate(encodedKeySpec);
    } catch (InvalidKeySpecException e) {
      attemptedAlgorithms.add("DSA: " + e.getMessage());
      throw new KeyStoreException(
          "Failed to load private key with any supported algorithm. Attempts: "
              + attemptedAlgorithms,
          e);
    }
  }

  private static byte[] base64Decode(String base64) {
    return getMimeDecoder().decode(base64.getBytes(US_ASCII));
  }
}
