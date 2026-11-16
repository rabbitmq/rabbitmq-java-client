// Copyright (c) 2026 Broadcom. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;

public class ConnectionFactoryHostnameVerificationTest {

  @Test
  void noTlsNoHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    assertThat(endpointIdentificationAlgorithm(cf)).isNull();
  }

  @Test
  void plainSocketFactoryDoesNotEnableHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.setSocketFactory(SocketFactory.getDefault());
    assertThat(endpointIdentificationAlgorithm(cf)).isNull();
  }

  @Test
  void sslSocketFactoryEnablesHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.setSocketFactory(SSLContext.getDefault().getSocketFactory());
    assertThat(endpointIdentificationAlgorithm(cf)).isEqualTo("HTTPS");
  }

  @Test
  void useSslProtocolEnablesHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.useSslProtocol(SSLContext.getDefault());
    assertThat(endpointIdentificationAlgorithm(cf)).isEqualTo("HTTPS");
  }

  @Test
  void useTlsWithNoVerificationDisablesHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.enableHostnameVerification();
    cf.useTlsWithNoVerification();
    assertThat(endpointIdentificationAlgorithm(cf)).isNull();
  }

  @Test
  void setSocketConfiguratorKeepsHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.useSslProtocol(SSLContext.getDefault());
    AtomicInteger calls = new AtomicInteger();
    cf.setSocketConfigurator(s -> calls.incrementAndGet());
    assertThat(endpointIdentificationAlgorithm(cf)).isEqualTo("HTTPS");
    assertThat(calls).hasValue(1);
  }

  @Test
  void hostnameVerificationIsAppliedOnlyOnce() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.useSslProtocol(SSLContext.getDefault());
    cf.enableHostnameVerification();
    cf.setSocketFactory(SSLContext.getDefault().getSocketFactory());
    SSLSocket socket = mock(SSLSocket.class);
    when(socket.getSSLParameters()).thenReturn(new SSLParameters());
    cf.effectiveSocketConfigurator().configure(socket);
    verify(socket, times(1)).setSSLParameters(any(SSLParameters.class));
  }

  @Test
  void cloneKeepsHostnameVerification() throws Exception {
    ConnectionFactory cf = new ConnectionFactory();
    cf.setSocketFactory(SSLContext.getDefault().getSocketFactory());
    assertThat(endpointIdentificationAlgorithm(cf.clone())).isEqualTo("HTTPS");
  }

  private static String endpointIdentificationAlgorithm(ConnectionFactory cf) throws IOException {
    SocketFactory socketFactory =
        cf.getSocketFactory() == null ? SocketFactory.getDefault() : cf.getSocketFactory();
    try (Socket socket = socketFactory.createSocket()) {
      cf.effectiveSocketConfigurator().configure(socket);
      if (socket instanceof SSLSocket) {
        return ((SSLSocket) socket).getSSLParameters().getEndpointIdentificationAlgorithm();
      } else {
        return null;
      }
    }
  }
}
