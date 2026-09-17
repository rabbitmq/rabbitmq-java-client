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
package com.rabbitmq.client.test.ssl;

import static com.rabbitmq.client.test.ssl.TlsTestUtils.caCertificate;
import static com.rabbitmq.client.test.ssl.TlsTestUtils.clientCertificate;
import static com.rabbitmq.client.test.ssl.TlsTestUtils.clientKey;
import static com.rabbitmq.client.test.ssl.TlsTestUtils.hostname;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.SocketConfigurator;
import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.TestUtils.ErlangVersionAtLeast;
import io.netty.channel.Channel;
import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.OpenSslSession;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import java.io.IOException;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;

class Pqc {

  String group = "X25519MLKEM768";
  String cipher = "TLS_AES_256_GCM_SHA384";
  String protocol = "TLSv1.3";

  @BeforeAll
  static void init() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
    if (Security.getProvider(BouncyCastleJsseProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleJsseProvider());
    }
  }

  @Test
  @EnabledForJreRange(minVersion = 27)
  @ErlangVersionAtLeast(28)
  void pqcBlockingIoJsse() throws Exception {
    AtomicReference<SSLSocket> socket = new AtomicReference<>();
    SSLContext sslContext = TlsTestUtils.verifiedSslContext();
    ConnectionFactory cf = TestUtils.connectionFactory();
    cf.useBlockingIo();
    cf.useSslProtocol(sslContext);

    SocketConfigurator sc =
        cf.getSocketConfigurator()
            .andThen(
                s -> {
                  if (s instanceof SSLSocket) {
                    SSLSocket sslSocket = (SSLSocket) s;
                    SSLParameters sslParameters = sslSocket.getSSLParameters();
                    sslParameters = sslParameters == null ? new SSLParameters() : sslParameters;
                    // to compile on Java < 20
                    TlsTestUtils.setNamesGroups(sslParameters, new String[] {group});
                    sslParameters.setCipherSuites(new String[] {cipher});
                    sslSocket.setSSLParameters(sslParameters);
                    socket.set(sslSocket);
                  }
                });
    cf.setSocketConfigurator(sc);

    try (Connection conn = conn(cf)) {
      assertTrue(conn.isOpen());
      assertThat(socket).doesNotHaveNullValue();
      SSLSession session = socket.get().getSession();
      assertThat(session.getCipherSuite()).isEqualTo(cipher);
      assertThat(session.getProtocol()).isEqualTo(protocol);
    }
  }

  @Test
  @ErlangVersionAtLeast(28)
  void pqcBlockingIoBouncyCastle() throws Exception {
    AtomicReference<SSLSocket> socket = new AtomicReference<>();
    SSLContext sslContext =
        TlsTestUtils.verifiedSslContext(
            () -> SSLContext.getInstance("TLS", BouncyCastleJsseProvider.PROVIDER_NAME));
    ConnectionFactory cf = TestUtils.connectionFactory();
    cf.useBlockingIo();
    cf.useSslProtocol(sslContext);

    SocketConfigurator sc =
        cf.getSocketConfigurator()
            .andThen(
                s -> {
                  if (s instanceof SSLSocket) {
                    SSLSocket sslSocket = (SSLSocket) s;
                    SSLParameters sslParameters = sslSocket.getSSLParameters();
                    sslParameters = sslParameters == null ? new SSLParameters() : sslParameters;
                    // to compile on Java < 20
                    TlsTestUtils.setNamesGroups(sslParameters, new String[] {group});
                    sslParameters.setCipherSuites(new String[] {cipher});
                    // Bouncycastle is stricter than JSSE for hostname verification
                    sslParameters.setServerNames(
                        Collections.singletonList(new SNIHostName(hostname())));
                    sslSocket.setSSLParameters(sslParameters);
                    socket.set(sslSocket);
                  }
                });
    cf.setSocketConfigurator(sc);
    try (Connection conn = conn(cf)) {
      assertTrue(conn.isOpen());
      assertThat(socket).doesNotHaveNullValue();
      SSLSession session = socket.get().getSession();
      assertThat(session.getCipherSuite()).isEqualTo(cipher);
      assertThat(session.getProtocol()).isEqualTo(protocol);
    }
  }

  @Test
  @EnabledForJreRange(minVersion = 27)
  @ErlangVersionAtLeast(28)
  void pqcNettyJsse() throws Exception {
    SslContext context =
        SslContextBuilder.forClient()
            .sslProvider(SslProvider.JDK)
            .trustManager(caCertificate())
            .keyManager(clientKey(), clientCertificate())
            .ciphers(singletonList(cipher))
            .build();

    ConnectionFactory cf = TestUtils.connectionFactory();
    AtomicReference<Channel> channel = new AtomicReference<>();
    cf.netty()
        .sslContext(context)
        .channelCustomizer(
            ch -> {
              channel.set(ch);
              SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
              if (sslHandler != null) {
                SSLParameters sslParams = sslHandler.engine().getSSLParameters();
                // to compile on Java < 20
                TlsTestUtils.setNamesGroups(sslParams, new String[] {group});
                sslHandler.engine().setSSLParameters(sslParams);
              }
            });

    try (Connection conn = conn(cf)) {
      assertTrue(conn.isOpen());
      assertThat(channel).doesNotHaveNullValue();
      Channel ch = channel.get();
      SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
      assertThat(sslHandler).isNotNull();
      SSLSession session = sslHandler.engine().getSession();
      assertThat(session.getCipherSuite()).isEqualTo(cipher);
      assertThat(session.getProtocol()).isEqualTo(protocol);
    }
  }

  @Test
  @ErlangVersionAtLeast(28)
  void pqcNettyOpenSsl() throws Exception {
    SslContext context =
        SslContextBuilder.forClient()
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(caCertificate())
            .keyManager(clientKey(), clientCertificate())
            .option(OpenSslContextOption.GROUPS, new String[] {group})
            .ciphers(singletonList(cipher))
            .build();

    ConnectionFactory cf = TestUtils.connectionFactory();
    AtomicReference<Channel> channel = new AtomicReference<>();
    cf.netty().sslContext(context).channelCustomizer(channel::set);

    try (Connection conn = conn(cf)) {
      assertTrue(conn.isOpen());
      assertThat(channel).doesNotHaveNullValue();
      Channel ch = channel.get();
      SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
      assertThat(sslHandler).isNotNull();
      SSLSession session = sslHandler.engine().getSession();
      assertThat(session.getCipherSuite()).isEqualTo(cipher);
      assertThat(session.getProtocol()).isEqualTo(protocol);
      assertThat(session).isInstanceOf(OpenSslSession.class);
      OpenSslSession openSslSession = (OpenSslSession) session;
      assertThat(openSslSession.getNamedGroup())
          .as("Negotiated TLS key exchange group")
          .isEqualTo(group);
    }
  }

  @Test
  @ErlangVersionAtLeast(28)
  void pqcNettyBouncyCastle() throws Exception {
    java.security.Provider bcJsseProvider = new BouncyCastleJsseProvider();
    SslContext context =
        SslContextBuilder.forClient()
            .sslProvider(SslProvider.JDK)
            .sslContextProvider(bcJsseProvider)
            .trustManager(caCertificate())
            .keyManager(clientKey(), clientCertificate())
            .ciphers(singletonList(cipher))
            .build();

    ConnectionFactory cf = TestUtils.connectionFactory();
    AtomicReference<Channel> channel = new AtomicReference<>();
    cf.netty()
        .sslContext(context)
        .channelCustomizer(
            ch -> {
              channel.set(ch);
              SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
              if (sslHandler != null) {
                SSLParameters sslParams = sslHandler.engine().getSSLParameters();
                // to compile on Java < 20
                TlsTestUtils.setNamesGroups(sslParams, new String[] {group});
                sslHandler.engine().setSSLParameters(sslParams);
              }
            });

    try (Connection conn = conn(cf)) {
      assertTrue(conn.isOpen());
      assertThat(channel).doesNotHaveNullValue();
      Channel ch = channel.get();
      SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
      assertThat(sslHandler).isNotNull();
      SSLSession session = sslHandler.engine().getSession();
      assertThat(session.getCipherSuite()).isEqualTo(cipher);
      assertThat(session.getProtocol()).isEqualTo(protocol);
      assertThat(session.getClass().getName()).containsIgnoringCase("bouncycastle");
    }
  }

  private static Connection conn(ConnectionFactory cf) throws IOException, TimeoutException {
    return cf.newConnection(
        () ->
            singletonList(new Address("localhost", ConnectionFactory.DEFAULT_AMQP_OVER_SSL_PORT)));
  }
}
