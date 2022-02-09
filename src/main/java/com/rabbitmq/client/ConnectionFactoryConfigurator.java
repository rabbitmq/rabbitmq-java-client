// Copyright (c) 2017-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.nio.NioParams;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class to load {@link ConnectionFactory} settings from a property file.
 * <p>
 * The authorised keys are the constants values in this class (e.g. USERNAME).
 * The property file/properties instance/map instance keys can have
 * a prefix, the default being <code>rabbitmq.</code>.
 * <p>
 * Property files can be loaded from the file system (the default),
 * but also from the classpath, by using the <code>classpath:</code> prefix
 * in the location.
 * <p>
 * Client properties can be set by using
 * the <code>client.properties.</code> prefix, e.g. <code>client.properties.app.name</code>.
 * Default client properties and custom client properties are merged. To remove
 * a default client property, set its key to an empty value.
 *
 * @see ConnectionFactory#load(String, String)
 * @since 5.1.0
 */
public class ConnectionFactoryConfigurator {

    public static final String DEFAULT_PREFIX = "rabbitmq.";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password"; //NOSONAR
    public static final String VIRTUAL_HOST = "virtual.host";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String CONNECTION_CHANNEL_MAX = "connection.channel.max";
    public static final String CONNECTION_FRAME_MAX = "connection.frame.max";
    public static final String CONNECTION_HEARTBEAT = "connection.heartbeat";
    public static final String CONNECTION_TIMEOUT = "connection.timeout";
    public static final String HANDSHAKE_TIMEOUT = "handshake.timeout";
    public static final String SHUTDOWN_TIMEOUT = "shutdown.timeout";
    public static final String CLIENT_PROPERTIES_PREFIX = "client.properties.";
    public static final String CONNECTION_RECOVERY_ENABLED = "connection.recovery.enabled";
    public static final String TOPOLOGY_RECOVERY_ENABLED = "topology.recovery.enabled";
    public static final String CONNECTION_RECOVERY_INTERVAL = "connection.recovery.interval";
    public static final String CHANNEL_RPC_TIMEOUT = "channel.rpc.timeout";
    public static final String CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE = "channel.should.check.rpc.response.type";
    public static final String USE_NIO = "use.nio";
    public static final String NIO_READ_BYTE_BUFFER_SIZE = "nio.read.byte.buffer.size";
    public static final String NIO_WRITE_BYTE_BUFFER_SIZE = "nio.write.byte.buffer.size";
    public static final String NIO_NB_IO_THREADS = "nio.nb.io.threads";
    public static final String NIO_WRITE_ENQUEUING_TIMEOUT_IN_MS = "nio.write.enqueuing.timeout.in.ms";
    public static final String NIO_WRITE_QUEUE_CAPACITY = "nio.write.queue.capacity";
    public static final String SSL_ALGORITHM = "ssl.algorithm";
    public static final String SSL_ENABLED = "ssl.enabled";
    public static final String SSL_KEY_STORE = "ssl.key.store";
    public static final String SSL_KEY_STORE_PASSWORD = "ssl.key.store.password";
    public static final String SSL_KEY_STORE_TYPE = "ssl.key.store.type";
    public static final String SSL_KEY_STORE_ALGORITHM = "ssl.key.store.algorithm";
    public static final String SSL_TRUST_STORE = "ssl.trust.store";
    public static final String SSL_TRUST_STORE_PASSWORD = "ssl.trust.store.password";
    public static final String SSL_TRUST_STORE_TYPE = "ssl.trust.store.type";
    public static final String SSL_TRUST_STORE_ALGORITHM = "ssl.trust.store.algorithm";
    public static final String SSL_VALIDATE_SERVER_CERTIFICATE = "ssl.validate.server.certificate";
    public static final String SSL_VERIFY_HOSTNAME = "ssl.verify.hostname";

    // aliases allow to be compatible with keys from Spring Boot and still be consistent with
    // the initial naming of the keys
    private static final Map<String, List<String>> ALIASES = new ConcurrentHashMap<String, List<String>>() {{
        put(SSL_KEY_STORE, Arrays.asList("ssl.key-store"));
        put(SSL_KEY_STORE_PASSWORD, Arrays.asList("ssl.key-store-password"));
        put(SSL_KEY_STORE_TYPE, Arrays.asList("ssl.key-store-type"));
        put(SSL_KEY_STORE_ALGORITHM, Arrays.asList("ssl.key-store-algorithm"));
        put(SSL_TRUST_STORE, Arrays.asList("ssl.trust-store"));
        put(SSL_TRUST_STORE_PASSWORD, Arrays.asList("ssl.trust-store-password"));
        put(SSL_TRUST_STORE_TYPE, Arrays.asList("ssl.trust-store-type"));
        put(SSL_TRUST_STORE_ALGORITHM, Arrays.asList("ssl.trust-store-algorithm"));
        put(SSL_VALIDATE_SERVER_CERTIFICATE, Arrays.asList("ssl.validate-server-certificate"));
        put(SSL_VERIFY_HOSTNAME, Arrays.asList("ssl.verify-hostname"));
    }};

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory cf, String propertyFileLocation, String prefix) throws IOException {
        if (propertyFileLocation == null || propertyFileLocation.isEmpty()) {
            throw new IllegalArgumentException("Property file argument cannot be null or empty");
        }
        Properties properties = new Properties();
        try (InputStream in = loadResource(propertyFileLocation)) {
            properties.load(in);
        }
        load(cf, (Map) properties, prefix);
    }

    private static InputStream loadResource(String location) throws FileNotFoundException {
        if (location.startsWith("classpath:")) {
            return ConnectionFactoryConfigurator.class.getResourceAsStream(
                    location.substring("classpath:".length())
            );
        } else {
            return new FileInputStream(location);
        }
    }

    public static void load(ConnectionFactory cf, Map<String, String> properties, String prefix) {
        prefix = prefix == null ? "" : prefix;
        String uri = properties.get(prefix + "uri");
        if (uri != null) {
            try {
                cf.setUri(uri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: " + uri, e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: " + uri, e);
            } catch (KeyManagementException e) {
                throw new IllegalArgumentException("Error while setting AMQP URI: " + uri, e);
            }
        }
        String username = lookUp(USERNAME, properties, prefix);
        if (username != null) {
            cf.setUsername(username);
        }
        String password = lookUp(PASSWORD, properties, prefix);
        if (password != null) {
            cf.setPassword(password);
        }
        String vhost = lookUp(VIRTUAL_HOST, properties, prefix);
        if (vhost != null) {
            cf.setVirtualHost(vhost);
        }
        String host = lookUp(HOST, properties, prefix);
        if (host != null) {
            cf.setHost(host);
        }
        String port = lookUp(PORT, properties, prefix);
        if (port != null) {
            cf.setPort(Integer.valueOf(port));
        }
        String requestedChannelMax = lookUp(CONNECTION_CHANNEL_MAX, properties, prefix);
        if (requestedChannelMax != null) {
            cf.setRequestedChannelMax(Integer.valueOf(requestedChannelMax));
        }
        String requestedFrameMax = lookUp(CONNECTION_FRAME_MAX, properties, prefix);
        if (requestedFrameMax != null) {
            cf.setRequestedFrameMax(Integer.valueOf(requestedFrameMax));
        }
        String requestedHeartbeat = lookUp(CONNECTION_HEARTBEAT, properties, prefix);
        if (requestedHeartbeat != null) {
            cf.setRequestedHeartbeat(Integer.valueOf(requestedHeartbeat));
        }
        String connectionTimeout = lookUp(CONNECTION_TIMEOUT, properties, prefix);
        if (connectionTimeout != null) {
            cf.setConnectionTimeout(Integer.valueOf(connectionTimeout));
        }
        String handshakeTimeout = lookUp(HANDSHAKE_TIMEOUT, properties, prefix);
        if (handshakeTimeout != null) {
            cf.setHandshakeTimeout(Integer.valueOf(handshakeTimeout));
        }
        String shutdownTimeout = lookUp(SHUTDOWN_TIMEOUT, properties, prefix);
        if (shutdownTimeout != null) {
            cf.setShutdownTimeout(Integer.valueOf(shutdownTimeout));
        }

        Map<String, Object> clientProperties = new HashMap<String, Object>();
        Map<String, Object> defaultClientProperties = AMQConnection.defaultClientProperties();
        clientProperties.putAll(defaultClientProperties);

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix + CLIENT_PROPERTIES_PREFIX)) {
                String clientPropertyKey = entry.getKey().substring((prefix + CLIENT_PROPERTIES_PREFIX).length());
                if (defaultClientProperties.containsKey(clientPropertyKey) && (entry.getValue() == null || entry.getValue().trim().isEmpty())) {
                    // if default property and value is empty, remove this property
                    clientProperties.remove(clientPropertyKey);
                } else {
                    clientProperties.put(
                            clientPropertyKey,
                            entry.getValue()
                    );
                }
            }
        }
        cf.setClientProperties(clientProperties);

        String automaticRecovery = lookUp(CONNECTION_RECOVERY_ENABLED, properties, prefix);
        if (automaticRecovery != null) {
            cf.setAutomaticRecoveryEnabled(Boolean.valueOf(automaticRecovery));
        }
        String topologyRecovery = lookUp(TOPOLOGY_RECOVERY_ENABLED, properties, prefix);
        if (topologyRecovery != null) {
            cf.setTopologyRecoveryEnabled(Boolean.valueOf(topologyRecovery));
        }
        String networkRecoveryInterval = lookUp(CONNECTION_RECOVERY_INTERVAL, properties, prefix);
        if (networkRecoveryInterval != null) {
            cf.setNetworkRecoveryInterval(Long.valueOf(networkRecoveryInterval));
        }
        String channelRpcTimeout = lookUp(CHANNEL_RPC_TIMEOUT, properties, prefix);
        if (channelRpcTimeout != null) {
            cf.setChannelRpcTimeout(Integer.valueOf(channelRpcTimeout));
        }
        String channelShouldCheckRpcResponseType = lookUp(CHANNEL_SHOULD_CHECK_RPC_RESPONSE_TYPE, properties, prefix);
        if (channelShouldCheckRpcResponseType != null) {
            cf.setChannelShouldCheckRpcResponseType(Boolean.valueOf(channelShouldCheckRpcResponseType));
        }

        String useNio = lookUp(USE_NIO, properties, prefix);
        if (useNio != null && Boolean.valueOf(useNio)) {
            cf.useNio();

            NioParams nioParams = new NioParams();

            String readByteBufferSize = lookUp(NIO_READ_BYTE_BUFFER_SIZE, properties, prefix);
            if (readByteBufferSize != null) {
                nioParams.setReadByteBufferSize(Integer.valueOf(readByteBufferSize));
            }
            String writeByteBufferSize = lookUp(NIO_WRITE_BYTE_BUFFER_SIZE, properties, prefix);
            if (writeByteBufferSize != null) {
                nioParams.setWriteByteBufferSize(Integer.valueOf(writeByteBufferSize));
            }
            String nbIoThreads = lookUp(NIO_NB_IO_THREADS, properties, prefix);
            if (nbIoThreads != null) {
                nioParams.setNbIoThreads(Integer.valueOf(nbIoThreads));
            }
            String writeEnqueuingTime = lookUp(NIO_WRITE_ENQUEUING_TIMEOUT_IN_MS, properties, prefix);
            if (writeEnqueuingTime != null) {
                nioParams.setWriteEnqueuingTimeoutInMs(Integer.valueOf(writeEnqueuingTime));
            }
            String writeQueueCapacity = lookUp(NIO_WRITE_QUEUE_CAPACITY, properties, prefix);
            if (writeQueueCapacity != null) {
                nioParams.setWriteQueueCapacity(Integer.valueOf(writeQueueCapacity));
            }
            cf.setNioParams(nioParams);
        }

        String useSsl = lookUp(SSL_ENABLED, properties, prefix);
        if (useSsl != null && Boolean.valueOf(useSsl)) {
            setUpSsl(cf, properties, prefix);
        }
    }

    private static void setUpSsl(ConnectionFactory cf, Map<String, String> properties, String prefix) {
        String algorithm = lookUp(SSL_ALGORITHM, properties, prefix);
        String keyStoreLocation = lookUp(SSL_KEY_STORE, properties, prefix);
        String keyStorePassword = lookUp(SSL_KEY_STORE_PASSWORD, properties, prefix);
        String keyStoreType = lookUp(SSL_KEY_STORE_TYPE, properties, prefix, "PKCS12");
        String keyStoreAlgorithm = lookUp(SSL_KEY_STORE_ALGORITHM, properties, prefix, "SunX509");
        String trustStoreLocation = lookUp(SSL_TRUST_STORE, properties, prefix);
        String trustStorePassword = lookUp(SSL_TRUST_STORE_PASSWORD, properties, prefix);
        String trustStoreType = lookUp(SSL_TRUST_STORE_TYPE, properties, prefix, "JKS");
        String trustStoreAlgorithm = lookUp(SSL_TRUST_STORE_ALGORITHM, properties, prefix, "SunX509");
        String validateServerCertificate = lookUp(SSL_VALIDATE_SERVER_CERTIFICATE, properties, prefix);
        String verifyHostname = lookUp(SSL_VERIFY_HOSTNAME, properties, prefix);

        try {
            algorithm = algorithm == null ?
                    ConnectionFactory.computeDefaultTlsProtocol(SSLContext.getDefault().getSupportedSSLParameters().getProtocols()) : algorithm;
            boolean enableHostnameVerification = verifyHostname == null ? Boolean.FALSE : Boolean.valueOf(verifyHostname);

            if (keyStoreLocation == null && trustStoreLocation == null) {
                setUpBasicSsl(
                        cf,
                        validateServerCertificate == null ? Boolean.FALSE : Boolean.valueOf(validateServerCertificate),
                        enableHostnameVerification,
                        algorithm
                );
            } else {
                KeyManager[] keyManagers = configureKeyManagers(keyStoreLocation, keyStorePassword, keyStoreType, keyStoreAlgorithm);
                TrustManager[] trustManagers = configureTrustManagers(trustStoreLocation, trustStorePassword, trustStoreType, trustStoreAlgorithm);

                // create ssl context
                SSLContext sslContext = SSLContext.getInstance(algorithm);
                sslContext.init(keyManagers, trustManagers, null);

                cf.useSslProtocol(sslContext);

                if (enableHostnameVerification) {
                    cf.enableHostnameVerification();
                }
            }
        } catch (NoSuchAlgorithmException | IOException | CertificateException |
                UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            throw new IllegalStateException("Error while configuring TLS", e);
        }
    }

    private static KeyManager[] configureKeyManagers(String keystore, String keystorePassword, String keystoreType, String keystoreAlgorithm) throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException {
        char[] keyPassphrase = null;
        if (keystorePassword != null) {
            keyPassphrase = keystorePassword.toCharArray();
        }
        KeyManager[] keyManagers = null;
        if (keystore != null) {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            try (InputStream in = loadResource(keystore)) {
                ks.load(in, keyPassphrase);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(keystoreAlgorithm);
            kmf.init(ks, keyPassphrase);
            keyManagers = kmf.getKeyManagers();
        }
        return keyManagers;
    }

    private static TrustManager[] configureTrustManagers(String truststore, String truststorePassword, String truststoreType, String truststoreAlgorithm)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        char[] trustPassphrase = null;
        if (truststorePassword != null) {
            trustPassphrase = truststorePassword.toCharArray();
        }
        TrustManager[] trustManagers = null;
        if (truststore != null) {
            KeyStore tks = KeyStore.getInstance(truststoreType);
            try (InputStream in = loadResource(truststore)) {
                tks.load(in, trustPassphrase);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(truststoreAlgorithm);
            tmf.init(tks);
            trustManagers = tmf.getTrustManagers();
        }
        return trustManagers;
    }

    private static void setUpBasicSsl(ConnectionFactory cf, boolean validateServerCertificate, boolean verifyHostname, String sslAlgorithm) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        if (validateServerCertificate) {
            useDefaultTrustStore(cf, sslAlgorithm, verifyHostname);
        } else {
            if (sslAlgorithm == null) {
                cf.useSslProtocol();
            } else {
                cf.useSslProtocol(sslAlgorithm);
            }
        }
    }

    private static void useDefaultTrustStore(ConnectionFactory cf, String sslAlgorithm, boolean verifyHostname) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance(sslAlgorithm);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        cf.useSslProtocol(sslContext);
        if (verifyHostname) {
            cf.enableHostnameVerification();
        }
    }

    public static void load(ConnectionFactory connectionFactory, String propertyFileLocation) throws IOException {
        load(connectionFactory, propertyFileLocation, DEFAULT_PREFIX);
    }

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory connectionFactory, Properties properties) {
        load(connectionFactory, (Map) properties, DEFAULT_PREFIX);
    }

    @SuppressWarnings("unchecked")
    public static void load(ConnectionFactory connectionFactory, Properties properties, String prefix) {
        load(connectionFactory, (Map) properties, prefix);
    }

    public static void load(ConnectionFactory connectionFactory, Map<String, String> properties) {
        load(connectionFactory, properties, DEFAULT_PREFIX);
    }

    public static String lookUp(String key, Map<String, String> properties, String prefix) {
        return lookUp(key, properties, prefix, null);
    }

    public static String lookUp(String key, Map<String, String> properties, String prefix, String defaultValue) {
        String value = properties.get(prefix + key);
        if (value == null) {
            value = ALIASES.getOrDefault(key, Collections.emptyList()).stream()
                    .map(alias -> properties.get(prefix + alias))
                    .filter(v -> v != null)
                    .findFirst().orElse(defaultValue);
        }
        return value;
    }


}
