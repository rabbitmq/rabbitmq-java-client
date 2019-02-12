// Copyright (c) 2019 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client.test;

import org.junit.Test;
import org.mockito.Mockito;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static com.rabbitmq.client.impl.TlsUtils.extensionPrettyPrint;
import static org.assertj.core.api.Assertions.assertThat;

public class TlsUtilsTest {

    static final byte [] DOES_NOT_MATTER = new byte[0];

    @Test
    public void subjectKeyIdentifier() {
        // https://www.alvestrand.no/objectid/2.5.29.14.html
        byte[] derOctetString = new byte[]{
                4, 22, 4, 20, -2, -87, -45, -120, 29, -126, -88, -17, 95, -39, -122, 23, 10, -62, -54, -82, 113, -121, -70, -121
        }; // 04:16:04:14:FE:A9:D3:88:1D:82:A8:EF:5F:D9:86:17:0A:C2:CA:AE:71:87:BA:87
        assertThat(extensionPrettyPrint("2.5.29.14", derOctetString, null))
                .isEqualTo("SubjectKeyIdentifier = FE:A9:D3:88:1D:82:A8:EF:5F:D9:86:17:0A:C2:CA:AE:71:87:BA:87");
        // change the 3rd byte to mimic it's not a octet string, the whole array should be then hex-dumped
        derOctetString = new byte[]{
                4, 22, 3, 20, -2, -87, -45, -120, 29, -126, -88, -17, 95, -39, -122, 23, 10, -62, -54, -82, 113, -121, -70, -121
        }; // 04:16:04:14:FE:A9:D3:88:1D:82:A8:EF:5F:D9:86:17:0A:C2:CA:AE:71:87:BA:87
        assertThat(extensionPrettyPrint("2.5.29.14", derOctetString, null))
                .isEqualTo("SubjectKeyIdentifier = 04:16:03:14:FE:A9:D3:88:1D:82:A8:EF:5F:D9:86:17:0A:C2:CA:AE:71:87:BA:87");
    }

    @Test public void keyUsage() {
        // https://www.alvestrand.no/objectid/2.5.29.15.html
        // http://javadoc.iaik.tugraz.at/iaik_jce/current/iaik/asn1/BIT_STRING.html
        X509Certificate c = Mockito.mock(X509Certificate.class);
        Mockito.when(c.getKeyUsage())
                .thenReturn(new boolean[] {true,false,true,false,false,false,false,false,false})
                .thenReturn(new boolean[] {false,false,false,false,false,true,true,false,false})
                .thenReturn(null);
        assertThat(extensionPrettyPrint("2.5.29.15", DOES_NOT_MATTER, c))
                .isEqualTo("KeyUsage = digitalSignature/keyEncipherment");
        assertThat(extensionPrettyPrint("2.5.29.15", DOES_NOT_MATTER, c))
                .isEqualTo("KeyUsage = keyCertSign/cRLSign");
        // change the 3rd byte to mimic it's not a bit string, the whole array should be then hex-dumped
        byte[] derOctetString = new byte[] { 4, 4, 3, 2, 1, 6}; // 04:04:03:02:01:06 => Certificate Sign, CRL Sign
        assertThat(extensionPrettyPrint("2.5.29.15", derOctetString, c))
                .isEqualTo("KeyUsage = 04:04:03:02:01:06");
    }

    @Test public void basicConstraints() {
        // https://www.alvestrand.no/objectid/2.5.29.19.html
        byte [] derOctetString = new byte [] {0x04, 0x02, 0x30, 0x00};
        assertThat(extensionPrettyPrint("2.5.29.19", derOctetString, null))
                .isEqualTo("BasicConstraints = CA:FALSE");
        derOctetString = new byte [] {4, 5, 48, 3, 1, 1, -1}; // 04:05:30:03:01:01:FF
        assertThat(extensionPrettyPrint("2.5.29.19", derOctetString, null))
                .isEqualTo("BasicConstraints = CA:TRUE");
        derOctetString = new byte [] {4, 5, 48, 3, 1, 1, 0}; // 04:05:30:03:01:01:00
        assertThat(extensionPrettyPrint("2.5.29.19", derOctetString, null))
                .isEqualTo("BasicConstraints = CA:FALSE");
        // change the 3rd to mimic it's not what the utils expects, the whole array should be hex-dump
        derOctetString = new byte [] {4, 5, 4, 3, 1, 1, 0}; // 04:05:04:03:01:01:00
        assertThat(extensionPrettyPrint("2.5.29.19", derOctetString, null))
                .isEqualTo("BasicConstraints = 04:05:04:03:01:01:00");

    }

    @Test public void authorityKeyIdentifier() {
        // https://www.alvestrand.no/objectid/2.5.29.35.html
        byte[] derOctetString = new byte[]{
                4,24,48,22,-128,20,-5,-46,124,99,-33,127,-44,-92,-114,-102,32,67,-11,-36,117,111,-74,-40,81,111
        }; // 04:18:30:16:80:14:FB:D2:7C:63:DF:7F:D4:A4:8E:9A:20:43:F5:DC:75:6F:B6:D8:51:6F
        assertThat(extensionPrettyPrint("2.5.29.35", derOctetString, null))
                .isEqualTo("AuthorityKeyIdentifier = keyid:FB:D2:7C:63:DF:7F:D4:A4:8E:9A:20:43:F5:DC:75:6F:B6:D8:51:6F");

        // add a byte to mimic not-expected length, the whole array should be hex-dump
        derOctetString = new byte[]{
                4,24,48,22,-128,20,-5,-46,124,99,-33,127,-44,-92,-114,-102,32,67,-11,-36,117,111,-74,-40,81,111, -1
        }; // 04:18:30:16:80:14:FB:D2:7C:63:DF:7F:D4:A4:8E:9A:20:43:F5:DC:75:6F:B6:D8:51:6F
        assertThat(extensionPrettyPrint("2.5.29.35", derOctetString, null))
                .isEqualTo("AuthorityKeyIdentifier = 04:18:30:16:80:14:FB:D2:7C:63:DF:7F:D4:A4:8E:9A:20:43:F5:DC:75:6F:B6:D8:51:6F:FF");
    }

    @Test public void extendedKeyUsage() throws CertificateParsingException {
        // https://www.alvestrand.no/objectid/2.5.29.37.html
        X509Certificate c = Mockito.mock(X509Certificate.class);
        Mockito.when(c.getExtendedKeyUsage())
                .thenReturn(Arrays.asList("1.3.6.1.5.5.7.3.1"))
                .thenReturn(Arrays.asList("1.3.6.1.5.5.7.3.1", "1.3.6.1.5.5.7.3.2"))
                .thenReturn(Arrays.asList("1.3.6.1.5.5.7.3.unknown"))
                .thenReturn(null)
                .thenThrow(CertificateParsingException.class);

        assertThat(extensionPrettyPrint("2.5.29.37", DOES_NOT_MATTER, c))
                .isEqualTo("ExtendedKeyUsage = TLS Web server authentication");
        assertThat(extensionPrettyPrint("2.5.29.37", DOES_NOT_MATTER, c))
                .isEqualTo("ExtendedKeyUsage = TLS Web server authentication/TLS Web client authentication");
        assertThat(extensionPrettyPrint("2.5.29.37", DOES_NOT_MATTER, c))
                .isEqualTo("ExtendedKeyUsage = 1.3.6.1.5.5.7.3.unknown");
        byte [] derOctetString = new byte[] {0x04, 0x0C, 0x30, 0x0A, 0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x03, 0x01};
        assertThat(extensionPrettyPrint("2.5.29.37", derOctetString, c))
                .isEqualTo("ExtendedKeyUsage = 04:0C:30:0A:06:08:2B:06:01:05:05:07:03:01");
        assertThat(extensionPrettyPrint("2.5.29.37", DOES_NOT_MATTER, c))
                .isEqualTo("ExtendedKeyUsage = <parsing-error>");
    }

}
