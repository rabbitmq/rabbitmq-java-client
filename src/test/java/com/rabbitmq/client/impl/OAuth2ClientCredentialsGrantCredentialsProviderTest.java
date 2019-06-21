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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.junit.After;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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

        int expiresIn = 60;

        ContextHandler context = new ContextHandler();
        context.setContextPath("/uaa/oauth/token/");
        context.setHandler(new AbstractHandler() {

            @Override
            public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response)
                    throws IOException {

                httpMethod.set(request.getMethod());
                contentType.set(request.getContentType());
                authorization.set(request.getHeader("authorization"));
                accept.set(request.getHeader("accept"));

                accessToken.set(UUID.randomUUID().toString());
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

        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider(
                "http://localhost:" + port + "/uaa/oauth/token/",
                "rabbit_client", "rabbit_secret",
                "password", // UAA-specific, standard is client_credentials
                "rabbit_super", "rabbit_super" // UAA-specific, to distinguish between RabbitMQ users
        );

        String password = provider.getPassword();

        assertThat(password).isEqualTo(accessToken.get());
        assertThat(provider.getExpiration()).isBetween(offsetNow(expiresIn - 10), offsetNow(expiresIn + 10));

        assertThat(httpMethod).hasValue("POST");
        assertThat(contentType).hasValue("application/x-www-form-urlencoded");
        assertThat(authorization).hasValue("Basic cmFiYml0X2NsaWVudDpyYWJiaXRfc2VjcmV0");
        assertThat(accept).hasValue("application/json");
    }

    @Test
    public void refresh() throws Exception {
        AtomicInteger retrieveTokenCallCount = new AtomicInteger(0);
        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider(
                "http://localhost:8080/uaa/oauth/token/",
                "rabbit_client", "rabbit_secret",
                "password", // UAA-specific, standard is client_credentials
                "rabbit_super", "rabbit_super" // UAA-specific, to distinguish between RabbitMQ users
        ) {
            @Override
            protected Token retrieveToken() {
                retrieveTokenCallCount.incrementAndGet();
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return new Token(UUID.randomUUID().toString(), new Date());
            }
        };

        Set<String> passwords = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(5);
        IntStream.range(0, 5).forEach(i -> new Thread(() -> {
            passwords.add(provider.getPassword());
            latch.countDown();
        }).start());

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(retrieveTokenCallCount).hasValue(1);
        assertThat(passwords).hasSize(1);
    }

    @Test
    public void parseToken() {
        OAuth2ClientCredentialsGrantCredentialsProvider provider = new OAuth2ClientCredentialsGrantCredentialsProvider(
                "http://localhost:8080/uaa/oauth/token",
                "rabbit_client", "rabbit_secret",
                "password", // UAA-specific, standard is client_credentials
                "rabbit_super", "rabbit_super" // UAA-specific, to distinguish between RabbitMQ users
        );

        String accessToken = "18c1b1dfdda04382a8bcc14d077b71dd";
        int expiresIn = 43199;
        String response = sampleJsonToken(accessToken, expiresIn);

        OAuth2ClientCredentialsGrantCredentialsProvider.Token token = provider.parseToken(response);
        assertThat(token.getAccess()).isEqualTo("18c1b1dfdda04382a8bcc14d077b71dd");
        assertThat(token.getExpiration()).isBetween(offsetNow(expiresIn - 10), offsetNow(expiresIn + 1));
    }

    Date offsetNow(int seconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, seconds);
        return calendar.getTime();
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

}
