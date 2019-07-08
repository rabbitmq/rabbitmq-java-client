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
import com.rabbitmq.client.TrustEverythingTrustManager;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

import static com.rabbitmq.client.ConnectionFactory.computeDefaultTlsProtocol;

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
 * TLS is supported by providing a <code>HTTPS</code> URI and setting a {@link SSLContext}. See
 * {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder#tls()} for more information.
 * <em>Applications in production should always use HTTPS to retrieve tokens.</em>
 * <p>
 * If more customization is needed, a {@link #connectionConfigurator} callback can be provided to configure
 * the connection.
 *
 * @see RefreshProtectedCredentialsProvider
 * @see CredentialsRefreshService
 * @see OAuth2ClientCredentialsGrantCredentialsProviderBuilder
 * @see OAuth2ClientCredentialsGrantCredentialsProviderBuilder#tls()
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

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     */
    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>());
    }

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     * @param parameters
     */
    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, null, null, null);
    }

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     * @param parameters
     * @param connectionConfigurator
     */
    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           Consumer<HttpURLConnection> connectionConfigurator) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, null, null, connectionConfigurator);
    }

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     * @param hostnameVerifier
     * @param sslSocketFactory
     */
    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, new HashMap<>(), hostnameVerifier, sslSocketFactory, null);
    }

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     * @param parameters
     * @param hostnameVerifier
     * @param sslSocketFactory
     */
    public OAuth2ClientCredentialsGrantCredentialsProvider(String tokenEndpointUri, String clientId, String clientSecret, String grantType, Map<String, String> parameters,
                                                           HostnameVerifier hostnameVerifier, SSLSocketFactory sslSocketFactory) {
        this(tokenEndpointUri, clientId, clientSecret, grantType, parameters, hostnameVerifier, sslSocketFactory, null);
    }

    /**
     * Use {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder} to create an instance.
     *
     * @param tokenEndpointUri
     * @param clientId
     * @param clientSecret
     * @param grantType
     * @param parameters
     * @param hostnameVerifier
     * @param sslSocketFactory
     * @param connectionConfigurator
     */
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
            builder.append(encode(name, UTF_8_CHARSET))
                    .append("=")
                    .append(encode(value, UTF_8_CHARSET));
        }
        return builder;
    }

    private static String encode(String value, String charset) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, charset);
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
            Instant receivedAt = Instant.now();
            return new Token(map.get("access_token").toString(), expiresIn, receivedAt);
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
    protected Duration timeBeforeExpiration(Token token) {
        return token.getTimeBeforeExpiration();
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

        private final int expiresIn;

        private final Instant receivedAt;

        public Token(String access, int expiresIn, Instant receivedAt) {
            this.access = access;
            this.expiresIn = expiresIn;
            this.receivedAt = receivedAt;
        }

        public String getAccess() {
            return access;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public Duration getTimeBeforeExpiration() {
            Instant now = Instant.now();
            long age = receivedAt.until(now, ChronoUnit.SECONDS);
            return Duration.ofSeconds(expiresIn - age);
        }
    }

    /**
     * Helper to create {@link OAuth2ClientCredentialsGrantCredentialsProvider} instances.
     */
    public static class OAuth2ClientCredentialsGrantCredentialsProviderBuilder {

        private final Map<String, String> parameters = new HashMap<>();
        private String tokenEndpointUri;
        private String clientId;
        private String clientSecret;
        private String grantType = "client_credentials";

        private Consumer<HttpURLConnection> connectionConfigurator;

        private TlsConfiguration tlsConfiguration = new TlsConfiguration(this);

        /**
         * Set the URI to request to get the token.
         *
         * @param tokenEndpointUri
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder tokenEndpointUri(String tokenEndpointUri) {
            this.tokenEndpointUri = tokenEndpointUri;
            return this;
        }

        /**
         * Set the OAuth 2 client ID
         * <p>
         * The client ID usually identifies the application that requests a token.
         *
         * @param clientId
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Set the secret (password) to use to get a token.
         *
         * @param clientSecret
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /**
         * Set the grant type to use when requesting the token.
         * <p>
         * The default is <code>client_credentials</code>, but some OAuth 2 servers can use
         * non-standard grant types to request tokens with extra-information.
         *
         * @param grantType
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder grantType(String grantType) {
            this.grantType = grantType;
            return this;
        }

        /**
         * Extra parameters to pass in the request.
         * <p>
         * These parameters can be used by the OAuth 2 server to narrow down the identify of the user.
         *
         * @param name
         * @param value
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder parameter(String name, String value) {
            this.parameters.put(name, value);
            return this;
        }

        /**
         * A hook to configure the {@link HttpURLConnection} before the request is sent.
         * <p>
         * Can be used to configuration settings like timeouts.
         *
         * @param connectionConfigurator
         * @return this builder instance
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder connectionConfigurator(Consumer<HttpURLConnection> connectionConfigurator) {
            this.connectionConfigurator = connectionConfigurator;
            return this;
        }

        /**
         * Get access to the TLS configuration to get the token on HTTPS.
         * <p>
         * It is recommended that applications in production use HTTPS and configure it properly
         * to perform token retrieval. Not doing so could result in sensitive data
         * transiting in clear on the network.
         * <p>
         * You can "exit" the TLS configuration and come back to the builder by
         * calling {@link TlsConfiguration#builder()}.
         *
         * @return the TLS configuration for this builder.
         * @see TlsConfiguration
         * @see TlsConfiguration#builder()
         */
        public TlsConfiguration tls() {
            return this.tlsConfiguration;
        }

        /**
         * Create the {@link OAuth2ClientCredentialsGrantCredentialsProvider} instance.
         *
         * @return
         */
        public OAuth2ClientCredentialsGrantCredentialsProvider build() {
            return new OAuth2ClientCredentialsGrantCredentialsProvider(
                    tokenEndpointUri, clientId, clientSecret, grantType, parameters,
                    tlsConfiguration.hostnameVerifier, tlsConfiguration.sslSocketFactory(),
                    connectionConfigurator
            );
        }

    }

    /**
     * TLS configuration for a {@link OAuth2ClientCredentialsGrantCredentialsProvider}.
     * <p>
     * Use it from {@link OAuth2ClientCredentialsGrantCredentialsProviderBuilder#tls()}.
     */
    public static class TlsConfiguration {

        private final OAuth2ClientCredentialsGrantCredentialsProviderBuilder builder;

        private HostnameVerifier hostnameVerifier;

        private SSLSocketFactory sslSocketFactory;

        private SSLContext sslContext;

        public TlsConfiguration(OAuth2ClientCredentialsGrantCredentialsProviderBuilder builder) {
            this.builder = builder;
        }

        /**
         * Set the hostname verifier.
         * <p>
         * {@link HttpsURLConnection} sets a default hostname verifier, so
         * setting a custom one is only needed for specific cases.
         *
         * @param hostnameVerifier
         * @return this TLS configuration instance
         * @see HostnameVerifier
         */
        public TlsConfiguration hostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * Set the {@link SSLSocketFactory} to use in the {@link HttpsURLConnection}.
         * <p>
         * The {@link SSLSocketFactory} supersedes the {@link SSLContext} value if both are set up.
         *
         * @param sslSocketFactory
         * @return this TLS configuration instance
         */
        public TlsConfiguration sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        /**
         * Set the {@link SSLContext} to use to create the {@link SSLSocketFactory} for the {@link HttpsURLConnection}.
         * <p>
         * This is the preferred way to configure TLS version to use, trusted servers, etc.
         * <p>
         * Note the {@link SSLContext} is not used if the {@link SSLSocketFactory} is set.
         *
         * @param sslContext
         * @return this TLS configuration instances
         */
        public TlsConfiguration sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Set up a non-secured environment, useful for development and testing.
         * <p>
         * With this configuration, all servers are trusted.
         *
         * <em>DO NOT USE this in production.</em>
         *
         * @return a TLS configuration that trusts all servers
         */
        public TlsConfiguration dev() {
            try {
                SSLContext sslContext = SSLContext.getInstance(computeDefaultTlsProtocol(
                        SSLContext.getDefault().getSupportedSSLParameters().getProtocols()
                ));
                sslContext.init(null, new TrustManager[]{new TrustEverythingTrustManager()}, null);
                this.sslContext = sslContext;
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new OAuthTokenManagementException("Error while creating TLS context for development configuration", e);
            }
            return this;
        }

        /**
         * Go back to the builder to configure non-TLS settings.
         *
         * @return the wrapping builder
         */
        public OAuth2ClientCredentialsGrantCredentialsProviderBuilder builder() {
            return builder;
        }

        private SSLSocketFactory sslSocketFactory() {
            if (this.sslSocketFactory != null) {
                return this.sslSocketFactory;
            } else if (this.sslContext != null) {
                return this.sslContext.getSocketFactory();
            }
            return null;
        }

    }

}
