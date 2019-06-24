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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 *
 * @see RefreshProtectedCredentialsProvider
 */
public class OAuth2ClientCredentialsGrantCredentialsProvider extends RefreshProtectedCredentialsProvider<OAuth2ClientCredentialsGrantCredentialsProvider.Token> {

    private static final String UTF_8_CHARSET = "UTF-8";
    private final String tokenEndpointUri;
    private final String clientId;
    private final String clientSecret;
    private final String grantType;

    private final Map<String, String> parameters;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String id;

    private final HostnameVerifier hostnameVerifier;
    private final SSLSocketFactory sslSocketFactory;

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>());
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>(), hostnameVerifier, sslSocketFactory);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, null, null);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this.tokenEndpointUri = tokenEndpointUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = grantType;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
        this.hostnameVerifier = hostnameVerifier;
        this.sslSocketFactory = sslSocketFactory;
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
    protected String usernameFromToken(Token token) {
        return "";
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
    protected Token retrieveToken() {
        try {
            StringBuilder urlParameters = new StringBuilder();
            encode(urlParameters, "grant_type", grantType);
            for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                encode(urlParameters, parameter.getKey(), parameter.getValue());
            }
            byte[] postData = urlParameters.toString().getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(tokenEndpointUri);

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

            configureHttpConnection(conn);

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
    protected String passwordFromToken(Token token) {
        return token.getAccess();
    }

    @Override
    protected Date expirationFromToken(Token token) {
        return token.getExpiration();
    }

    protected void configureHttpConnection(HttpURLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection securedConnection = (HttpsURLConnection) connection;
            if (this.hostnameVerifier != null) {
                securedConnection.setHostnameVerifier(this.hostnameVerifier);
            }
            if (this.sslSocketFactory != null) {
                securedConnection.setSSLSocketFactory(this.sslSocketFactory);
            }
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

    public static class OAuth2ClientCredentialsGrantCredentialsProviderBuilder {

        private final Map<String, String> parameters = new HashMap<>();
        private String tokenEndpointUri;
        private String clientId;
        private String clientSecret;
        private String grantType = "client_credentials";
        private HostnameVerifier hostnameVerifier;

        private SSLSocketFactory sslSocketFactory;

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder tokenEndpointUri(String tokenEndpointUri) {
            this.tokenEndpointUri = tokenEndpointUri;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder grantType(String grantType) {
            this.grantType = grantType;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder parameter(String name, String value) {
            this.parameters.put(name, value);
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder setHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProvider build() {
            return new OAuth2ClientCredentialsGrantCredentialsProvider(
                    tokenEndpointUri, clientId, clientSecret, grantType, parameters,
                    hostnameVerifier, sslSocketFactory
            );
        }

    }
}
