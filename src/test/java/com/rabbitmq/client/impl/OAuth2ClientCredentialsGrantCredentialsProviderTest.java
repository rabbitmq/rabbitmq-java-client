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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.test.TestUtils;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class OAuth2ClientCredentialsGrantCredentialsProviderTest {

    Server server;

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void getToken() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        int port = TestUtils.randomNetworkPort();
        connector.setPort(port);
        server.setConnectors(new Connector[]{connector});

        AtomicReference<String> httpMethod = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> accessToken = new AtomicReference<>();
        AtomicReference<Map<String, String[]>> httpParameters = new AtomicReference<>();

        int expiresIn = 60;

        ContextHandler context = new ContextHandler();
        context.setContextPath("/uaa/oauth/token");
        context.setHandler(new AbstractHandler() {

            @Override
            public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response)
                    throws IOException {
                httpMethod.set(request.getMethod());
                contentType.set(request.getContentType());
                authorization.set(request.getHeader("authorization"));
                accept.set(request.getHeader("accept"));

                accessToken.set(UUID.randomUUID().toString());

                httpParameters.set(request.getParameterMap());

                String json = sampleJsonToken(accessToken.get(), expiresIn);

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLength(json.length());
                response.setContentType("application/json");

                response.getWriter().print(json);

                request.setHandled(true);
            }
        });

        server.setHandler(context);

        server.setStopTimeout(1000);
        server.start();

        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider.OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
                .tokenEndpointUri("http://localhost:" + port + "/uaa/oauth/token/")
                .clientId("rabbit_client").clientSecret("rabbit_secret")
                .grantType("password")
                .parameter("username", "rabbit_super")
                .parameter("password", "rabbit_super")
                .build();

        String password = provider.getPassword();

        assertThat(password).isEqualTo(accessToken.get());
        assertThat(provider.getTimeBeforeExpiration()).isBetween(Duration.ofSeconds(expiresIn - 10), Duration.ofSeconds(expiresIn + 10));

        assertThat(httpMethod).hasValue("POST");
        assertThat(contentType).hasValue("application/x-www-form-urlencoded");
        assertThat(authorization).hasValue("Basic cmFiYml0X2NsaWVudDpyYWJiaXRfc2VjcmV0");
        assertThat(accept).hasValue("application/json");
        Map<String, String[]> parameters = httpParameters.get();
        assertThat(parameters).isNotNull().hasSize(3).containsKeys("grant_type", "username", "password")
                .hasEntrySatisfying("grant_type", v -> assertThat(v).hasSize(1).contains("password"))
                .hasEntrySatisfying("username", v -> assertThat(v).hasSize(1).contains("rabbit_super"))
                .hasEntrySatisfying("password", v -> assertThat(v).hasSize(1).contains("rabbit_super"));
    }

    @Test
    public void tls() throws Exception {
        int port = TestUtils.randomNetworkPort();

        String accessToken = UUID.randomUUID().toString();
        int expiresIn = 60;

        AbstractHandler httpHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                String json = sampleJsonToken(accessToken, expiresIn);

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLength(json.length());
                response.setContentType("application/json");

                response.getWriter().print(json);

                baseRequest.setHandled(true);
            }
        };

        KeyStore keyStore = startHttpsServer(port, httpHandler);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keyStore);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, tmf.getTrustManagers(), null);

        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider.OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
                .tokenEndpointUri("https://localhost:" + port + "/uaa/oauth/token/")
                .clientId("rabbit_client").clientSecret("rabbit_secret")
                .tls().sslContext(sslContext).builder()
                .build();

        String password = provider.getPassword();
        assertThat(password).isEqualTo(accessToken);
        assertThat(provider.getTimeBeforeExpiration()).isBetween(Duration.ofSeconds(expiresIn - 10), Duration.ofSeconds(expiresIn + 10));
    }

    @Test
    public void parseToken() {
        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider(
                "http://localhost:8080/uaa/oauth/token/",
                "rabbit_client", "rabbit_secret",
                "client_credentials"
        );

        String accessToken = "18c1b1dfdda04382a8bcc14d077b71dd";
        int expiresIn = 43199;
        String response = sampleJsonToken(accessToken, expiresIn);

        OAuth2ClientCredentialsGrantCredentialsProvider.Token token = provider.parseToken(response);
        assertThat(token.getAccess()).isEqualTo("18c1b1dfdda04382a8bcc14d077b71dd");
        assertThat(token.getTimeBeforeExpiration()).isBetween(Duration.ofSeconds(expiresIn - 10), Duration.ofSeconds(expiresIn + 1));
    }

    String sampleJsonToken(String accessToken, int expiresIn) {
        String json = "{\n" +
                "  \"access_token\" : \"{accessToken}\",\n" +
                "  \"token_type\" : \"bearer\",\n" +
                "  \"expires_in\" : {expiresIn},\n" +
                "  \"scope\" : \"clients.read emails.write scim.userids password.write idps.write notifications.write oauth.login scim.write critical_notifications.write\",\n" +
                "  \"jti\" : \"18c1b1dfdda04382a8bcc14d077b71dd\"\n" +
                "}";
        return json.replace("{accessToken}", accessToken).replace("{expiresIn}", expiresIn + "");
    }

    KeyStore startHttpsServer(int port, Handler handler) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        String keyStorePassword = "password";
        keyStore.load(null, keyStorePassword.toCharArray());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                new X500NameBuilder().addRDN(BCStyle.CN, "localhost").build(),
                BigInteger.valueOf(new SecureRandom().nextInt()),
                Date.from(Instant.now().minus(10, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(10, ChronoUnit.DAYS)),
                new X500NameBuilder().addRDN(BCStyle.CN, "localhost").build(),
                kp.getPublic()
        );

        X509CertificateHolder certificateHolder = certificateBuilder.build(new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .build(kp.getPrivate()));

        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certificateHolder);

        keyStore.setKeyEntry("default", kp.getPrivate(), keyStorePassword.toCharArray(), new Certificate[]{certificate});

        server = new Server();
        SslContextFactory sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(keyStorePassword);

        HttpConfiguration httpsConfiguration = new HttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(port);
        httpsConfiguration.setOutputBufferSize(32768);

        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        httpsConfiguration.addCustomizer(src);

        ServerConnector https = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfiguration));
        https.setPort(port);
        https.setIdleTimeout(500000);

        server.setConnectors(new Connector[]{https});

        ContextHandler context = new ContextHandler();
        context.setContextPath("/uaa/oauth/token");
        context.setHandler(handler);

        server.setHandler(context);

        server.start();
        return keyStore;
    }

}
