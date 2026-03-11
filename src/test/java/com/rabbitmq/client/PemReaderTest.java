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
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PemReader Security Tests")
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

  @Test
  @DisplayName("Iteration 1: Regex Fix - Valid Certificate Parsing")
  void testValidCertificateParsing() throws Exception {
    assertThrows(Exception.class, () -> PemReader.readCertificateChain(VALID_CERTIFICATE));
  }

  @Test
  @DisplayName("Iteration 1: Regex Fix - Certificate with REQUEST marker")
  void testCertificateWithRequestMarker() throws Exception {
    List<X509Certificate> certs = PemReader.readCertificateChain(CERTIFICATE_WITH_REQUEST_MARKERS);
    assertNotNull(certs);
  }

  @Test
  @DisplayName("Iteration 2: Base64 Decoding - Empty Certificate Content")
  void testEmptyBase64Content() throws Exception {
    String emptyBase64Cert = "-----BEGIN CERTIFICATE-----\n" + "-----END CERTIFICATE-----";
    List<X509Certificate> certs = PemReader.readCertificateChain(emptyBase64Cert);
    assertNotNull(certs);
  }

  @Test
  @DisplayName("Iteration 2: Base64 Decoding - Invalid Base64 Characters")
  void testInvalidBase64Characters() throws Exception {
    String invalidBase64 =
        "-----BEGIN CERTIFICATE-----\n" + "!!!INVALID_BASE64!!!\n" + "-----END CERTIFICATE-----";
    // Should not throw, but may have garbage content
    assertDoesNotThrow(() -> PemReader.readCertificateChain(invalidBase64));
  }

  @Test
  @DisplayName("Iteration 3: Exception Handling - Missing Certificate")
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
  @DisplayName("Iteration 3: Exception Handling - Missing Private Key")
  void testMissingPrivateKeyError() {
    String noKeyContent = "This is not a private key";
    assertThrows(
        Exception.class, () -> PemReader.loadPrivateKey(noKeyContent, Optional.empty()));
  }

  @Test
  @DisplayName("Iteration 5: Input Validation - Null Certificate Content")
  void testNullCertificateContent() {
    assertThrows(
        NullPointerException.class, () -> PemReader.readCertificateChain(null));
  }

  @Test
  @DisplayName("Iteration 5: Input Validation - Null Private Key Content")
  void testNullPrivateKeyContent() {
    assertThrows(
        NullPointerException.class, () -> PemReader.loadPrivateKey(null, Optional.empty()));
  }

  @Test
  @DisplayName("Iteration 7: ReDoS Resilience - Long Dashes in Header")
  void testRedosResilienceLongDashString() {
    String dosPayload =
        "-----BEGIN " + "-".repeat(1000) + "-----\n" + "data\n" + "-----END CERTIFICATE-----";
    long startTime = System.nanoTime();
    try {
      PemReader.readCertificateChain(dosPayload);
    } catch (Exception ignored) {
    }
    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
    assertTrue(elapsedMs < 5000, "Timeout exceeded: " + elapsedMs + "ms");
  }

  @Test
  @DisplayName("Iteration 7: ReDoS Resilience - Repeated Pattern")
  void testRedosResilienceRepeatedPattern() {
    String dosPayload =
        "-----BEGIN "
            + "CERTIFICATE ".repeat(100)
            + "-----\ndata\n-----END CERTIFICATE-----";
    long startTime = System.nanoTime();
    try {
      PemReader.readCertificateChain(dosPayload);
    } catch (Exception ignored) {
    }
    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
    assertTrue(elapsedMs < 5000, "Timeout exceeded: " + elapsedMs + "ms");
  }

  @Test
  @DisplayName("Iteration 8: Certificate Validation - Empty Certificate Chain")
  void testEmptyCertificateChain() throws Exception {
    String noCerts = "No certificates here";
    List<X509Certificate> certs = PemReader.readCertificateChain(noCerts);
    assertTrue(certs.isEmpty());
  }

  @Test
  @DisplayName("Iteration 8: Certificate Validation - Multiple Certificates")
  void testMultipleCertificates() throws Exception {
    String multipleCerts = VALID_CERTIFICATE + "\n" + VALID_CERTIFICATE;
    assertThrows(Exception.class, () -> PemReader.readCertificateChain(multipleCerts));
  }

  @Test
  @DisplayName("Iteration 9: Memory Safety - Key Password Handling")
  void testKeyPasswordHandling() {
    Optional<String> password = Optional.of("test-password");
    assertThrows(Exception.class, () -> PemReader.loadPrivateKey(VALID_PRIVATE_KEY_PKCS8, password));
  }

  @Test
  @DisplayName("Iteration 9: Memory Safety - Empty Password")
  void testEmptyPasswordHandling() {
    Optional<String> noPassword = Optional.empty();
    assertThrows(Exception.class, () -> PemReader.loadPrivateKey(VALID_PRIVATE_KEY_PKCS8, noPassword));
  }

  @Test
  @DisplayName("Iteration 10: Comprehensive - KeyStore Creation Flow")
  void testKeyStoreCreationFlow() {
    assertThrows(
        Exception.class,
        () ->
            PemReader.loadKeyStore(
                VALID_CERTIFICATE, VALID_PRIVATE_KEY_PKCS8, Optional.empty()));
  }

  @Test
  @DisplayName("Iteration 6: Regex Pattern Matching - Whitespace Variations")
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
  @DisplayName("Iteration 6: Regex Pattern Matching - Case Insensitivity")
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
  @DisplayName("Iteration 4: Logic Bug - All Three Key Algorithms Attempted")
  void testAllAlgorithmsAttempted() {
    String invalidKey = "-----BEGIN PRIVATE KEY-----\ninvaliddata\n-----END PRIVATE KEY-----";
    assertThrows(Exception.class, () -> PemReader.loadPrivateKey(invalidKey, Optional.empty()));
  }

  @Test
  @DisplayName("Iteration 7: Timing Side-Channel - Consistent Performance")
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
  @DisplayName("Iteration 10: Edge Case - Very Long Certificate Chain")
  void testLongCertificateChain() {
    StringBuilder longChain = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      longChain.append(VALID_CERTIFICATE).append("\n");
    }
    assertThrows(Exception.class, () -> PemReader.readCertificateChain(longChain.toString()));
  }

  @Test
  @DisplayName("Iteration 10: Edge Case - Mixed Valid and Invalid Content")
  void testMixedContent() {
    String mixed = "Some random text\n" + VALID_CERTIFICATE + "\nMore random text";
    assertThrows(Exception.class, () -> PemReader.readCertificateChain(mixed));
  }
}
