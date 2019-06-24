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
import java.util.function.Consumer;

/**
 * A {@link CredentialsProvider} that performs an
 * <a href="https://tools.ietf.org/html/rfc6749#section-4.4">OAuth 2 Client Credentials flow</a>
 * to retrieve a token.
 * <p>
 * The provider has different parameters to set, e.g. the token endpoint URI of the OAuth server to
 * request, the client ID, the client secret, the grant type, etc. The {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder}
 * class is the preferred way to create an instance of the provider.
 * <p>
 * The implementation uses the JDK {@link HttpURLConnection} API to request the OAuth server. This can
 * be easily changed by overriding the {@link #retrieveToken()} method.
 * <p>
 * This class expects a JSON document as a response and needs <a href="https://github.com/FasterXML/jackson">Jackson</a>
 * to deserialize the response into a {@link Token}. This can be changed by overriding the {@link #parseToken(String)}
 * method.
 * <p>
 * TLS is supported by providing a <code>HTTPS</code> URI and setting the {@link HostnameVerifier} and {@link SSLSocketFactory}.
 * <p>
 * If more customization is needed, a {@link #connectionConfigurator} callback can be provided to configure
 * the connection.
 *
 * @see RefreshProtectedCredentialsProvider
 * @see CredentialsRefreshService
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

    private final Consumer<HttpURLConnection> connectionConfigurator;

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>());
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, null, null, null);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           Consumer<HttpURLConnection> connectionConfigurator) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, null, null, connectionConfigurator);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>(), hostnameVerifier, sslSocketFactory, null);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, hostnameVerifier, sslSocketFactory, null);
    }

    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory,
                                                           Consumer<HttpURLConnection> connectionConfigurator) {
        this.tokenEndpointUri = tokenEndpointUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = grantType;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
        this.hostnameVerifier = hostnameVerifier;
        this.sslSocketFactory = sslSocketFactory;
        this.connectionConfigurator = connectionConfigurator == null ? c -> {
        } : connectionConfigurator;
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
            conn.setConnectTimeout(60_000);
            conn.setReadTimeout(60_000);

            configureConnection(conn);

            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
            }

            checkResponseCode(conn.getResponseCode());
            checkContentType(conn.getHeaderField("content-type"));

            return parseToken(extractResponseBody(conn.getInputStream()));
        } catch (IOException e) {
            throw new OAuthTokenManagementException("Error while retrieving OAuth 2 token", e);
        }
    }

    protected void checkContentType(String headerField) throws OAuthTokenManagementException {
        if (headerField == null || !headerField.toLowerCase().contains("json")) {
            throw new OAuthTokenManagementException(
                    "HTTP request for token retrieval is not JSON: " + headerField
            );
        }
    }

    protected void checkResponseCode(int responseCode) throws OAuthTokenManagementException {
        if (responseCode != 200) {
            throw new OAuthTokenManagementException(
                    "HTTP request for token retrieval did not " +
                            "return 200 response code: " + responseCode
            );
        }
    }

    protected String extractResponseBody(InputStream inputStream) throws IOException {
        StringBuffer content = new StringBuffer();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
        }
        return content.toString();
    }

    @Override
    protected String passwordFromToken(Token token) {
        return token.getAccess();
    }

    @Override
    protected Date expirationFromToken(Token token) {
        return token.getExpiration();
    }

    protected void configureConnection(HttpURLConnection connection) {
        this.connectionConfigurator.accept(connection);
        this.configureConnectionForHttps(connection);
    }

    protected void configureConnectionForHttps(HttpURLConnection connection) {
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

        private Consumer<HttpURLConnection> connectionConfigurator;

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

        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder setConnectionConfigurator(Consumer<HttpURLConnection> connectionConfigurator) {
            this.connectionConfigurator = connectionConfigurator;
            return this;
        }

        public OAuth2ClientCredentialsGrantCredentialsProvider build() {
            return new OAuth2ClientCredentialsGrantCredentialsProvider(
                    tokenEndpointUri, clientId, clientSecret, grantType, parameters,
                    hostnameVerifier, sslSocketFactory,
                    connectionConfigurator
            );
        }

    }
}
