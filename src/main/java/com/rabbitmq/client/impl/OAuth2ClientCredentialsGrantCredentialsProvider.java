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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class OAuth2ClientCredentialsGrantCredentialsProvider implements CredentialsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2ClientCredentialsGrantCredentialsProvider.class);

    private static final String UTF_8_CHARSET = "UTF-8";
    private final String serverUri; // should be renamed to tokenEndpointUri?
    private final String clientId;
    private final String clientSecret;
    private final String grantType;
    // UAA specific, to distinguish between different users
    private final String username, password;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String id;

    private final AtomicReference<Token> token = new AtomicReference<>();

    private final Lock refreshLock = new ReentrantLock();
    private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();
    private AtomicBoolean refreshInProcess = new AtomicBoolean(false);

    public OAuth2ClientCredentialsGrantCredentialsProvider(String serverUri, String clientId, String clientSecret, String grantType, String username, String password) {
        this.serverUri = serverUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = grantType;
        this.username = username;
        this.password = password;
        this.id = UUID.randomUUID().toString();
    }

    private static StringBuilder encode(StringBuilder builder, String name, String value) throws UnsupportedEncodingException {
        if (value != null) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(name, UTF_8_CHARSET))
                    .append("=")
                    .append(URLEncoder.encode(value, UTF_8_CHARSET));
        }
        return builder;
    }

    private static String basicAuthentication(String username, String password) {
        String credentials = username + ":" + password;
        byte[] credentialsAsBytes = credentials.getBytes(StandardCharsets.ISO_8859_1);
        byte[] encodedBytes = Base64.getEncoder().encode(credentialsAsBytes);
        String encodedCredentials = new String(encodedBytes, StandardCharsets.ISO_8859_1);
        return "Basic " + encodedCredentials;
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public String getPassword() {
        if (token.get() == null) {
            refresh();
        }
        return token.get().getAccess();
    }

    @Override
    public Date getExpiration() {
        if (token.get() == null) {
            refresh();
        }
        return token.get().getExpiration();
    }

    protected Token parseToken(String response) {
        try {
            Map<?, ?> map = objectMapper.readValue(response, Map.class);
            int expiresIn = ((Number) map.get("expires_in")).intValue();
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, expiresIn);
            return new Token(map.get("access_token").toString(), calendar.getTime());
        } catch (IOException e) {
            throw new OAuthTokenManagementException("Error while parsing OAuth 2 token", e);
        }
    }

    @Override
    public void refresh() {
        // refresh should happen at once. Other calls wait for the refresh to finish and move on.
        if (refreshLock.tryLock()) {
            LOGGER.debug("Refreshing token");
            try {
                latch.set(new CountDownLatch(1));
                refreshInProcess.set(true);
                token.set(retrieveToken());
                LOGGER.debug("Token refreshed");
            } finally {
                latch.get().countDown();
                refreshInProcess.set(false);
                refreshLock.unlock();
            }
        } else {
            try {
                LOGGER.debug("Waiting for token refresh to be finished");
                while (!refreshInProcess.get()) {
                    Thread.sleep(10);
                }
                latch.get().await();
                LOGGER.debug("Done waiting for token refresh");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected Token retrieveToken() {
        // FIXME handle TLS specific settings
        try {
            StringBuilder urlParameters = new StringBuilder();
            encode(urlParameters, "grant_type", grantType);
            encode(urlParameters, "username", username);
            encode(urlParameters, "password", password);
            byte[] postData = urlParameters.toString().getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(serverUri);
            // FIXME close connection?
            // FIXME set timeout on request
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("authorization", basicAuthentication(clientId, clientSecret));
            conn.setRequestProperty("content-type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", UTF_8_CHARSET);
            conn.setRequestProperty("accept", "application/json");
            conn.setRequestProperty("content-length", Integer.toString(postDataLength));
            conn.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new OAuthTokenManagementException(
                        "HTTP request for token retrieval did not " +
                                "return 200 response code: " + responseCode
                );
            }

            StringBuffer content = new StringBuffer();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            }

            // FIXME check result is json


            return parseToken(content.toString());
        } catch (IOException e) {
            throw new OAuthTokenManagementException("Error while retrieving OAuth 2 token", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OAuth2ClientCredentialsGrantCredentialsProvider that = (OAuth2ClientCredentialsGrantCredentialsProvider) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public static class Token {

        private final String access;

        private final Date expiration;

        public Token(String access, Date expiration) {
            this.access = access;
            this.expiration = expiration;
        }

        public Date getExpiration() {
            return expiration;
        }

        public String getAccess() {
            return access;
        }
    }
}
