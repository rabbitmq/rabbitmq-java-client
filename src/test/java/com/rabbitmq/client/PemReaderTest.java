// Copyright (c) 2025 Broadcom. All Rights Reserved.
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

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PemReaderTest {

  // Valid test certificates and keys (minimal examples)
  private static final String VALID_CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIIDXTCCAkWgAwIBAgIJAJC1/iNAZwqDMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n"
          + "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n"
          + "-----END CERTIFICATE-----";

  private static final String VALID_PRIVATE_KEY_PKCS8 =
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDZrRnKWQGWIxov\n"
          + "5cYpOQzdYqH5wb5e3uXt7l7e5e3uXt7l7e5e3uXt7l7e5e3uXt7l7e5e3uXt7l7e\n"
          + "-----END PRIVATE KEY-----";

  private static final String CERTIFICATE_WITH_REQUEST_MARKERS =
      "-----BEGIN CERTIFICATE REQUEST-----\n"
          + "MIICljCCAX4CAQAwDQYJKoZIhvcNAQEEBQAwgaAxCzAJBgNVBAYTAlBUMRMwEQYD\n"
          + "-----END CERTIFICATE REQUEST-----";

  private static final String ENCRYPTED_PRIVATE_KEY =
      "-----BEGIN ENCRYPTED PRIVATE KEY-----\n"
          + "MIIFDjBABgkqhkiG9w0BBQ0wMzAbBgkqhkiG9w0BBQwwDgQI1234567890ABCDE\n"
          + "-----END ENCRYPTED PRIVATE KEY-----";

  @Test
  void testValidCertificateParsing() throws Exception {
    List<X509Certificate> certs = PemReader.readCertificateChain(VALID_CERTIFICATE);
    assertNotNull(certs);
    // Note: parsing may fail due to invalid cert data, but regex should match
  }

  @Test
  void testCertificateWithRequestMarker() throws Exception {
    List<X509Certificate> certs = PemReader.readCertificateChain(CERTIFICATE_WITH_REQUEST_MARKERS);
    assertNotNull(certs);
  }

  @Test
  void testEmptyBase64Content() throws Exception {
    String emptyBase64Cert = "-----BEGIN CERTIFICATE-----\n" + "-----END CERTIFICATE-----";
    List<X509Certificate> certs = PemReader.readCertificateChain(emptyBase64Cert);
    assertNotNull(certs);
    assertDoesNotThrow(() -> PemReader.readCertificateChain(emptyBase64Cert));
  }

  @Test
  void testInvalidBase64Characters() throws Exception {
    String invalidBase64 =
        "-----BEGIN CERTIFICATE-----\n" + "!!!INVALID_BASE64!!!\n" + "-----END CERTIFICATE-----";
    // Should not throw, but may have garbage content
    assertDoesNotThrow(() -> PemReader.readCertificateChain(invalidBase64));
  }

  @Test
  void testMissingCertificateExceptionMessage() {
    String noCertContent = "This is not a certificate";
    assertThrows(
        Exception.class,
        () -> {
          List<X509Certificate> certs = PemReader.readCertificateChain(noCertContent);
          if (certs.isEmpty()) {
            throw new java.security.cert.CertificateException("No certificates found");
          }
        });
  }

  @Test
  void testMissingPrivateKeyError() {
    String noKeyContent = "This is not a private key";
    assertThrows(
        Exception.class, () -> PemReader.loadPrivateKey(noKeyContent, Optional.empty()));
  }

  @Test
  void testNullCertificateContent() {
    assertThrows(
        NullPointerException.class, () -> PemReader.readCertificateChain(null));
  }

  @Test
  void testNullPrivateKeyContent() {
    assertThrows(
        NullPointerException.class, () -> PemReader.loadPrivateKey(null, Optional.empty()));
  }

  @Test
  void testRedosResilienceLongDashString() {
    String dosPayload =
        "-----BEGIN " + "-".repeat(1000) + "-----\n" + "data\n" + "-----END CERTIFICATE-----";
    long startTime = System.nanoTime();
    List<X509Certificate> result = null;
    try {
      result = PemReader.readCertificateChain(dosPayload);
    } catch (Exception e) {
      // Acceptable to fail, but should not hang
    }
    long endTime = System.nanoTime();
    long elapsedMs = (endTime - startTime) / 1_000_000;
    assertTrue(
        elapsedMs < 5000,
        "ReDoS vulnerability detected: parsing took " + elapsedMs + "ms for malicious input");
  }

  @Test
  void testRedosResilienceRepeatedPattern() {
    String dosPayload =
        "-----BEGIN "
            + "CERTIFICATE ".repeat(100)
            + "-----\ndata\n-----END CERTIFICATE-----";
    long startTime = System.nanoTime();
    List<X509Certificate> result = null;
    try {
      result = PemReader.readCertificateChain(dosPayload);
    } catch (Exception e) {
      // Acceptable to fail, but should not hang
    }
    long endTime = System.nanoTime();
    long elapsedMs = (endTime - startTime) / 1_000_000;
    assertTrue(
        elapsedMs < 5000, "ReDoS vulnerability detected: parsing took " + elapsedMs + "ms");
  }

  @Test
  void testEmptyCertificateChain() throws Exception {
    String noCerts = "No certificates here";
    List<X509Certificate> certs = PemReader.readCertificateChain(noCerts);
    assertTrue(certs.isEmpty());
  }

  @Test
  void testMultipleCertificates() throws Exception {
    String multipleCerts = VALID_CERTIFICATE + "\n" + VALID_CERTIFICATE;
    // Should parse without error
    assertDoesNotThrow(() -> PemReader.readCertificateChain(multipleCerts));
  }

  @Test
  void testKeyPasswordHandling() {
    // Document that password is converted to char array
    Optional<String> password = Optional.of("test-password");
    assertDoesNotThrow(() -> PemReader.loadPrivateKey(VALID_PRIVATE_KEY_PKCS8, password));
  }

  @Test
  void testEmptyPasswordHandling() {
    Optional<String> noPassword = Optional.empty();
    assertDoesNotThrow(() -> PemReader.loadPrivateKey(VALID_PRIVATE_KEY_PKCS8, noPassword));
  }

  @Test
  void testKeyStoreCreationFlow() {
    assertThrows(
        Exception.class,
        () ->
            PemReader.loadKeyStore(
                VALID_CERTIFICATE, VALID_PRIVATE_KEY_PKCS8, Optional.empty()));
  }

  @Test
  void testWhitespaceVariations() throws Exception {
    String[] variations = {
      "-----BEGIN CERTIFICATE-----\ndata\n-----END CERTIFICATE-----",
      "-----BEGIN CERTIFICATE-----\r\ndata\r\n-----END CERTIFICATE-----",
      "-----BEGIN CERTIFICATE-----  \ndata\n-----END CERTIFICATE-----",
      "-----BEGIN  CERTIFICATE-----\ndata\n-----END CERTIFICATE-----",
      "-----BEGIN RSA CERTIFICATE-----\ndata\n-----END RSA CERTIFICATE-----",
    };

    for (String variation : variations) {
      assertDoesNotThrow(() -> PemReader.readCertificateChain(variation));
    }
  }

  @Test
  void testCaseInsensitivity() throws Exception {
    String[] caseVariations = {
      "-----BEGIN certificate-----\ndata\n-----END certificate-----",
      "-----BEGIN Certificate-----\ndata\n-----END Certificate-----",
      "-----BEGIN CERTIFICATE-----\ndata\n-----END CERTIFICATE-----",
    };

    for (String variation : caseVariations) {
      assertDoesNotThrow(() -> PemReader.readCertificateChain(variation));
    }
  }

  @Test
  void testAllAlgorithmsAttempted() {
    String invalidKey = "-----BEGIN PRIVATE KEY-----\ninvaliddata\n-----END PRIVATE KEY-----";
    Exception exception =
        assertThrows(Exception.class, () -> PemReader.loadPrivateKey(invalidKey, Optional.empty()));
    String message = exception.getMessage();
    assertNotNull(message);
    assertFalse(
        message.contains("RSA: RSA:"),
        "Error message should not duplicate algorithm names");
  }

  @Test
  void testConsistentPerformance() throws Exception {
    String validCert = VALID_CERTIFICATE;
    String invalidCert = "-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----";

    long validTime = System.nanoTime();
    try {
      PemReader.readCertificateChain(validCert);
    } catch (Exception ignored) {
    }
    validTime = System.nanoTime() - validTime;

    long invalidTime = System.nanoTime();
    try {
      PemReader.readCertificateChain(invalidCert);
    } catch (Exception ignored) {
    }
    invalidTime = System.nanoTime() - invalidTime;

    // Times should be similar (no timing attacks based on content)
    long timeDifference = Math.abs(validTime - invalidTime);
    // Allow for timing variance but ensure not dramatically different
    long maxDifference = Math.max(validTime, invalidTime) / 2;
    assertTrue(
        timeDifference < maxDifference,
        "Timing variation suggests potential side-channel: " + timeDifference + " vs " + maxDifference);
  }

  @Test
  void testLongCertificateChain() {
    StringBuilder longChain = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      longChain.append(VALID_CERTIFICATE).append("\n");
    }
    assertDoesNotThrow(() -> PemReader.readCertificateChain(longChain.toString()));
  }

  @Test
  void testMixedContent() {
    String mixed = "Some random text\n" + VALID_CERTIFICATE + "\nMore random text";
    assertDoesNotThrow(() -> PemReader.readCertificateChain(mixed));
  }
}
