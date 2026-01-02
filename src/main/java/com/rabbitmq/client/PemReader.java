// Copyright (c) 2017-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Base64.getMimeDecoder;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.crypto.Cipher.DECRYPT_MODE;

/**
 * Note: this class is copied from org.apache.zookeeper.util.PemReader (see
 * https://github.com/apache/zookeeper/blob/master/zookeeper-server/src/main/java/org/apache/zookeeper/util/PemReader.java)
 * under the same Apache License 2.0 (ASL).
 *
 * The following modifications have been made to the original source code:
 * <ul>
 * <li>removed methods around loading trustStores.</li>
 * <li>updated the regular expressions to avoid exponential backtracking.</li>
 * </ul>
 */
public final class PemReader {

    private static final Pattern CERT_PATTERN = Pattern.compile(
        "-+BEGIN\\s+.*CERTIFICATE[^-]*-+\\s*"  // Header
            + "([a-z0-9+/=\\r\\n]+)"           // Base64 text
            + "-+END\\s+.*CERTIFICATE[^-]*-+", // Footer
        CASE_INSENSITIVE);

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+\\s*"  // Header
            + "([a-z0-9+/=\\r\\n]+)"              // Base64 text
            + "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+", // Footer
        CASE_INSENSITIVE);

    private PemReader() {
    }

    public static KeyStore loadKeyStore(String certificateChainFile, String privateKeyFile, Optional<String> keyPassword) throws IOException, GeneralSecurityException {
        PrivateKey key = loadPrivateKey(privateKeyFile, keyPassword);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainFile);
        if (certificateChain.isEmpty()) {
            throw new CertificateException("Certificate file does not contain any certificates: "
                                           + certificateChainFile);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("key",
                             key,
                             keyPassword.orElse("").toCharArray(),
                             certificateChain.toArray(new Certificate[0]));
        return keyStore;
    }

    public static List<X509Certificate> readCertificateChain(String certificateChain) throws CertificateException {
        Matcher matcher = CERT_PATTERN.matcher(certificateChain);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();

        int start = 0;
        while (matcher.find(start)) {
            byte[] buffer = base64Decode(matcher.group(1));
            certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(buffer)));
            start = matcher.end();
        }

        return certificates;
    }

    public static PrivateKey loadPrivateKey(String privateKey, Optional<String> keyPassword) throws IOException, GeneralSecurityException {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(privateKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a private key");
        }
        byte[] encodedKey = base64Decode(matcher.group(1));

        PKCS8EncodedKeySpec encodedKeySpec;
        if (keyPassword.isPresent()) {
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(encodedKey);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
            SecretKey secretKey = keyFactory.generateSecret(new PBEKeySpec(keyPassword.get().toCharArray()));

            Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
            cipher.init(DECRYPT_MODE, secretKey, encryptedPrivateKeyInfo.getAlgParameters());

            encodedKeySpec = encryptedPrivateKeyInfo.getKeySpec(cipher);
        } else {
            encodedKeySpec = new PKCS8EncodedKeySpec(encodedKey);
        }

        // this code requires a key in PKCS8 format which is not the default openssl format
        // to convert to the PKCS8 format you use : openssl pkcs8 -topk8 ...
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException ignore) {
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(encodedKeySpec);
        } catch (InvalidKeySpecException ignore) {
        }

        KeyFactory keyFactory = KeyFactory.getInstance("DSA");
        return keyFactory.generatePrivate(encodedKeySpec);
    }

    private static byte[] base64Decode(String base64) {
        return getMimeDecoder().decode(base64.getBytes(US_ASCII));
    }

}
