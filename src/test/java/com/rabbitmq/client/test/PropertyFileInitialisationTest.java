// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.rabbitmq.client.impl.AMQConnection.defaultClientProperties;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
@RunWith(Parameterized.class)
public class PropertyFileInitialisationTest {

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[] {
            "./src/test/resources/property-file-initialisation/configuration.properties",
            "classpath:/property-file-initialisation/configuration.properties"
        };
    }

    @Parameterized.Parameter
    public String propertyFileLocation;

    ConnectionFactory cf = new ConnectionFactory();

    @Test public void propertyInitialisationFromFile() throws IOException {
        cf.load(propertyFileLocation);
        checkConnectionFactory();
    }

    @Test public void propertyInitialisationCustomPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("prefix.");

        cf.load(propertiesCustomPrefix, "prefix.");
        checkConnectionFactory();
    }

    @Test public void propertyInitialisationNoPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("");

        cf.load(propertiesCustomPrefix, "");
        checkConnectionFactory();
    }

    @Test public void propertyInitialisationNullPrefix() throws Exception {
        Properties propertiesCustomPrefix = getPropertiesWitPrefix("");

        cf.load(propertiesCustomPrefix, null);
        checkConnectionFactory();
    }

    @Test public void propertyInitialisationUri() {
        cf.load(Collections.singletonMap("rabbitmq.uri", "amqp://foo:bar@127.0.0.1:5673/dummy"));

        assertThat(cf.getUsername()).isEqualTo("foo");
        assertThat(cf.getPassword()).isEqualTo("bar");
        assertThat(cf.getVirtualHost()).isEqualTo("dummy");
        assertThat(cf.getHost()).isEqualTo("127.0.0.1");
        assertThat(cf.getPort()).isEqualTo(5673);
    }

    @Test public void propertyInitialisationIncludeDefaultClientPropertiesByDefault() {
        cf.load(new HashMap<>());
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size());
    }

    @Test public void propertyInitialisationAddCustomClientProperty() {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties.foo", "bar");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() + 1);
        assertThat(cf.getClientProperties()).extracting("foo").isEqualTo("bar");
    }

    @Test public void propertyInitialisationGetRidOfDefaultClientPropertyWithEmptyValue() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() - 1);
    }

    @Test public void propertyInitialisationOverrideDefaultClientProperty() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "whatever");
        }});
        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size());
        assertThat(cf.getClientProperties()).extracting(key).isEqualTo("whatever");
    }

    @Test public void propertyInitialisationDoNotUseNio() throws Exception {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.use.nio", "false");
            put("rabbitmq.nio.nb.io.threads", "2");
        }});
        assertThat(cf.getNioParams().getNbIoThreads()).isNotEqualTo(2);
    }

    private void checkConnectionFactory() {
        assertThat(cf.getUsername()).isEqualTo("foo");
        assertThat(cf.getPassword()).isEqualTo("bar");
        assertThat(cf.getVirtualHost()).isEqualTo("dummy");
        assertThat(cf.getHost()).isEqualTo("127.0.0.1");
        assertThat(cf.getPort()).isEqualTo(5673);

        assertThat(cf.getRequestedChannelMax()).isEqualTo(1);
        assertThat(cf.getRequestedFrameMax()).isEqualTo(2);
        assertThat(cf.getRequestedHeartbeat()).isEqualTo(10);
        assertThat(cf.getConnectionTimeout()).isEqualTo(10000);
        assertThat(cf.getHandshakeTimeout()).isEqualTo(5000);

        assertThat(cf.getClientProperties().entrySet()).hasSize(defaultClientProperties().size() + 1);
        assertThat(cf.getClientProperties()).extracting("foo").isEqualTo("bar");

        assertThat(cf.isAutomaticRecoveryEnabled()).isFalse();
        assertThat(cf.isTopologyRecoveryEnabled()).isFalse();
        assertThat(cf.getNetworkRecoveryInterval()).isEqualTo(10000l);
        assertThat(cf.getChannelRpcTimeout()).isEqualTo(10000);
        assertThat(cf.isChannelShouldCheckRpcResponseType()).isTrue();

        assertThat(cf.getNioParams()).isNotNull();
        assertThat(cf.getNioParams().getReadByteBufferSize()).isEqualTo(32000);
        assertThat(cf.getNioParams().getWriteByteBufferSize()).isEqualTo(32000);
        assertThat(cf.getNioParams().getNbIoThreads()).isEqualTo(2);
        assertThat(cf.getNioParams().getWriteEnqueuingTimeoutInMs()).isEqualTo(5000);
        assertThat(cf.getNioParams().getWriteQueueCapacity()).isEqualTo(1000);
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
