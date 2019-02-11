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

package com.rabbitmq.client.test.ssl;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.TlsUtils;
import com.rabbitmq.client.test.TestUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;

public class TlsConnectionLogging {

    @Test
    public void certificateInfoAreProperlyExtracted() throws Exception {
        SSLContext sslContext = TestUtils.getSSLContext();
        sslContext.init(null, new TrustManager[]{new AlwaysTrustTrustManager()}, null);
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.useSslProtocol(sslContext);
        connectionFactory.useBlockingIo();
        AtomicReference<SSLSocket> socketCaptor = new AtomicReference<>();
        connectionFactory.setSocketConfigurator(socket -> socketCaptor.set((SSLSocket) socket));
        try (Connection ignored = connectionFactory.newConnection()) {
            SSLSession session = socketCaptor.get().getSession();
            assertNotNull(session);
            String info = TlsUtils.peerCertificateInfo(session.getPeerCertificates()[0], "some prefix");
            Assertions.assertThat(info).contains("some prefix")
                    .contains("CN=")
                    .contains("X.509 usage extensions")
                    .contains("KeyUsage");

        }
    }

    private static class AlwaysTrustTrustManager implements X509TrustManager {
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
