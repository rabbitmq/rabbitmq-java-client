// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.test;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionFactoryConfigurator;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.rabbitmq.client.impl.AMQConnection.defaultClientProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 *
 */
public class PropertyFileInitialisationTest {

    ConnectionFactory cf = new ConnectionFactory();

    @Test
    public void propertyInitialisationFromFile() throws IOException {
        for (String propertyFileLocation : Arrays.asList(
                "./src/test/resources/property-file-initialisation/configuration.properties",
                "classpath:/property-file-initialisation/configuration.properties")) {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.load(propertyFileLocation);
            checkConnectionFactory(connectionFactory);
        }
    }

    @Test
    public void propertyInitialisationCustomPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("prefix.");

        cf.load(propertiesCustomPrefix, "prefix.");
        checkConnectionFactory();
    }

    @Test
    public void propertyInitialisationNoPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("");

        cf.load(propertiesCustomPrefix, "");
        checkConnectionFactory();
    }

    @Test
    public void propertyInitialisationNullPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("");

        cf.load(propertiesCustomPrefix, null);
        checkConnectionFactory();
    }

    @Test
    public void propertyInitialisationUri() {
        cf.load(Collections.singletonMap("rabbitmq.uri", "amqp://foo:bar@127.0.0.1:5673/dummy"));

        assertThat(cf.getUsername()).isEqualTo("foo");
        assertThat(cf.getPassword()).isEqualTo("bar");
        assertThat(cf.getVirtualHost()).isEqualTo("dummy");
        assertThat(cf.getHost()).isEqualTo("127.0.0.1");
        assertThat(cf.getPort()).isEqualTo(5673);
    }

    @Test
    public void propertyInitialisationIncludeDefaultClientPropertiesByDefault() {
        cf.load(new HashMap<>());
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size());
    }

    @Test
    public void propertyInitialisationAddCustomClientProperty() {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties.foo", "bar");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() + 1);
        assertThat(cf.getClientProperties()).extracting("foo").isEqualTo("bar");
    }

    @Test
    public void propertyInitialisationGetRidOfDefaultClientPropertyWithEmptyValue() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() - 1);
    }

    @Test
    public void propertyInitialisationOverrideDefaultClientProperty() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "whatever");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size());
        assertThat(cf.getClientProperties()).extracting(key).isEqualTo("whatever");
    }

    @Test
    public void propertyInitialisationDoNotUseNio() throws Exception {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.use.nio", "false");
            put("rabbitmq.nio.nb.io.threads", "2");
        }});
        assertThat(cf.getNioParams().getNbIoThreads()).isNotEqualTo(2);
    }

    @Test
    public void lookUp() {
        assertThat(ConnectionFactoryConfigurator.lookUp(
                ConnectionFactoryConfigurator.SSL_KEY_STORE,
                Collections.singletonMap(ConnectionFactoryConfigurator.SSL_KEY_STORE, "some file"),
                ""
        )).as("exact key should be looked up").isEqualTo("some file");

        assertThat(ConnectionFactoryConfigurator.lookUp(
                ConnectionFactoryConfigurator.SSL_KEY_STORE,
                Collections.emptyMap(),
                ""
        )).as("lookup should return null when no match").isNull();

        assertThat(ConnectionFactoryConfigurator.lookUp(
                ConnectionFactoryConfigurator.SSL_KEY_STORE,
                Collections.singletonMap("ssl.key-store", "some file"), // key alias
                ""
        )).as("alias key should be used when initial is missing").isEqualTo("some file");

        assertThat(ConnectionFactoryConfigurator.lookUp(
                ConnectionFactoryConfigurator.SSL_TRUST_STORE_TYPE,
                Collections.emptyMap(),
                "",
                "JKS"
        )).as("default value should be returned when key is not found").isEqualTo("JKS");
    }

    @Test
    public void tlsInitialisationWithKeyManagerAndTrustManagerShouldSucceed() {
        Stream.of("./src/test/resources/property-file-initialisation/tls/",
                "classpath:/property-file-initialisation/tls/").forEach(baseDirectory -> {
            Map<String, String> configuration = new HashMap<>();
            configuration.put(ConnectionFactoryConfigurator.SSL_ENABLED, "true");
            configuration.put(ConnectionFactoryConfigurator.SSL_KEY_STORE, baseDirectory + "keystore.p12");
            configuration.put(ConnectionFactoryConfigurator.SSL_KEY_STORE_PASSWORD, "bunnies");
            configuration.put(ConnectionFactoryConfigurator.SSL_KEY_STORE_TYPE, "PKCS12");
            configuration.put(ConnectionFactoryConfigurator.SSL_KEY_STORE_ALGORITHM, "SunX509");

            configuration.put(ConnectionFactoryConfigurator.SSL_TRUST_STORE, baseDirectory + "truststore.jks");
            configuration.put(ConnectionFactoryConfigurator.SSL_TRUST_STORE_PASSWORD, "bunnies");
            configuration.put(ConnectionFactoryConfigurator.SSL_TRUST_STORE_TYPE, "JKS");
            configuration.put(ConnectionFactoryConfigurator.SSL_TRUST_STORE_ALGORITHM, "SunX509");

            configuration.put(ConnectionFactoryConfigurator.SSL_VERIFY_HOSTNAME, "true");

            ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
            ConnectionFactoryConfigurator.load(connectionFactory, configuration, "");

            verify(connectionFactory, times(1)).useSslProtocol(any(SSLContext.class));
            verify(connectionFactory, times(1)).enableHostnameVerification();
        });
    }

    @Test
    public void tlsNotEnabledIfNotConfigured() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ConnectionFactoryConfigurator.load(connectionFactory, Collections.emptyMap(), "");
        verify(connectionFactory, never()).useSslProtocol(any(SSLContext.class));
    }

    @Test
    public void tlsNotEnabledIfDisabled() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ConnectionFactoryConfigurator.load(
                connectionFactory,
                Collections.singletonMap(ConnectionFactoryConfigurator.SSL_ENABLED, "false"),
                ""
        );
        verify(connectionFactory, never()).useSslProtocol(any(SSLContext.class));
    }

    @Test
    public void tlsSslContextSetIfTlsEnabled() {
        AtomicBoolean sslProtocolSet = new AtomicBoolean(false);
        ConnectionFactory connectionFactory = new ConnectionFactory() {
            @Override
            public void useSslProtocol(SSLContext context) {
                sslProtocolSet.set(true);
                super.useSslProtocol(context);
            }
        };
        ConnectionFactoryConfigurator.load(
                connectionFactory,
                Collections.singletonMap(ConnectionFactoryConfigurator.SSL_ENABLED, "true"),
                ""
        );
        assertThat(sslProtocolSet).isTrue();
    }

    @Test
    public void tlsBasicSetupShouldTrustEveryoneWhenServerValidationIsNotEnabled() throws Exception {
        String algorithm = ConnectionFactory.computeDefaultTlsProtocol(SSLContext.getDefault().getSupportedSSLParameters().getProtocols());
        Map<String, String> configuration = new HashMap<>();
        configuration.put(ConnectionFactoryConfigurator.SSL_ENABLED, "true");
        configuration.put(ConnectionFactoryConfigurator.SSL_VALIDATE_SERVER_CERTIFICATE, "false");
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ConnectionFactoryConfigurator.load(connectionFactory, configuration, "");
        verify(connectionFactory, times(1)).useSslProtocol(algorithm);
    }

    @Test
    public void tlsBasicSetupShouldSetDefaultTrustManagerWhenServerValidationIsEnabled() throws Exception {
        Map<String, String> configuration = new HashMap<>();
        configuration.put(ConnectionFactoryConfigurator.SSL_ENABLED, "true");
        configuration.put(ConnectionFactoryConfigurator.SSL_VALIDATE_SERVER_CERTIFICATE, "true");
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ConnectionFactoryConfigurator.load(connectionFactory, configuration, "");
        verify(connectionFactory, never()).useSslProtocol(anyString());
        verify(connectionFactory, times(1)).useSslProtocol(any(SSLContext.class));
    }

    private void checkConnectionFactory() {
        checkConnectionFactory(this.cf);
    }

    private void checkConnectionFactory(ConnectionFactory connectionFactory) {
        assertThat(connectionFactory.getUsername()).isEqualTo("foo");
        assertThat(connectionFactory.getPassword()).isEqualTo("bar");
        assertThat(connectionFactory.getVirtualHost()).isEqualTo("dummy");
        assertThat(connectionFactory.getHost()).isEqualTo("127.0.0.1");
        assertThat(connectionFactory.getPort()).isEqualTo(5673);

        assertThat(connectionFactory.getRequestedChannelMax()).isEqualTo(1);
        assertThat(connectionFactory.getRequestedFrameMax()).isEqualTo(2);
        assertThat(connectionFactory.getRequestedHeartbeat()).isEqualTo(10);
        assertThat(connectionFactory.getConnectionTimeout()).isEqualTo(10000);
        assertThat(connectionFactory.getHandshakeTimeout()).isEqualTo(5000);

        assertThat(connectionFactory.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() + 1);
        assertThat(connectionFactory.getClientProperties()).extracting("foo").isEqualTo("bar");

        assertThat(connectionFactory.isAutomaticRecoveryEnabled()).isFalse();
        assertThat(connectionFactory.isTopologyRecoveryEnabled()).isFalse();
        assertThat(connectionFactory.getNetworkRecoveryInterval()).isEqualTo(10000l);
        assertThat(connectionFactory.getChannelRpcTimeout()).isEqualTo(10000);
        assertThat(connectionFactory.isChannelShouldCheckRpcResponseType()).isTrue();

        assertThat(connectionFactory.getNioParams()).isNotNull();
        assertThat(connectionFactory.getNioParams().getReadByteBufferSize()).isEqualTo(32000);
        assertThat(connectionFactory.getNioParams().getWriteByteBufferSize()).isEqualTo(32000);
        assertThat(connectionFactory.getNioParams().getNbIoThreads()).isEqualTo(2);
        assertThat(connectionFactory.getNioParams().getWriteEnqueuingTimeoutInMs()).isEqualTo(5000);
        assertThat(connectionFactory.getNioParams().getWriteQueueCapacity()).isEqualTo(1000);
    }

    private Properties getPropertiesWitPrefix(String prefix) throws IOException {
        Properties properties = new Properties();
        Reader reader = null;
        try {
            reader = new FileReader("./src/test/resources/property-file-initialisation/configuration.properties");
            properties.load(reader);
        } finally {
            reader.close();
        }

        Properties propertiesCustomPrefix = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            propertiesCustomPrefix.put(
                    prefix + entry.getKey().toString().substring(ConnectionFactoryConfigurator.DEFAULT_PREFIX.length()),
                    entry.getValue()
            );
        }
        return propertiesCustomPrefix;
    }

}
