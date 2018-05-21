// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

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

        assertThat(cf.getUsername(), is("foo"));
        assertThat(cf.getPassword(), is("bar"));
        assertThat(cf.getVirtualHost(), is("dummy"));
        assertThat(cf.getHost(), is("127.0.0.1"));
        assertThat(cf.getPort(), is(5673));
    }

    @Test public void propertyInitialisationIncludeDefaultClientPropertiesByDefault() {
        cf.load(new HashMap<String, String>());
        assertThat(cf.getClientProperties().entrySet(), hasSize(defaultClientProperties().size()));
    }

    @Test public void propertyInitialisationAddCustomClientProperty() {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties.foo", "bar");
        }});
        assertThat(cf.getClientProperties().entrySet(), hasSize(defaultClientProperties().size() + 1));
        assertThat(cf.getClientProperties().get("foo").toString(), is("bar"));
    }

    @Test public void propertyInitialisationGetRidOfDefaultClientPropertyWithEmptyValue() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "");
        }});
        assertThat(cf.getClientProperties().entrySet(), hasSize(defaultClientProperties().size() - 1));
    }

    @Test public void propertyInitialisationOverrideDefaultClientProperty() {
        final String key = defaultClientProperties().entrySet().iterator().next().getKey();
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.client.properties." + key, "whatever");
        }});
        assertThat(cf.getClientProperties().entrySet(), hasSize(defaultClientProperties().size()));
        assertThat(cf.getClientProperties().get(key).toString(), is("whatever"));
    }

    @Test public void propertyInitialisationDoNotUseNio() throws Exception {
        cf.load(new HashMap<String, String>() {{
            put("rabbitmq.use.nio", "false");
            put("rabbitmq.nio.nb.io.threads", "2");
        }});
        assertThat(cf.getNioParams().getNbIoThreads(), not(2));
    }

    private void checkConnectionFactory() {
        assertThat(cf.getUsername(), is("foo"));
        assertThat(cf.getPassword(), is("bar"));
        assertThat(cf.getVirtualHost(), is("dummy"));
        assertThat(cf.getHost(), is("127.0.0.1"));
        assertThat(cf.getPort(), is(5673));

        assertThat(cf.getRequestedChannelMax(), is(1));
        assertThat(cf.getRequestedFrameMax(), is(2));
        assertThat(cf.getRequestedHeartbeat(), is(10));
        assertThat(cf.getConnectionTimeout(), is(10000));
        assertThat(cf.getHandshakeTimeout(), is(5000));

        assertThat(cf.getClientProperties().entrySet(), hasSize(defaultClientProperties().size() + 1));
        assertThat(cf.getClientProperties().get("foo").toString(), is("bar"));

        assertThat(cf.isAutomaticRecoveryEnabled(), is(false));
        assertThat(cf.isTopologyRecoveryEnabled(), is(false));
        assertThat(cf.getNetworkRecoveryInterval(), is(10000l));
        assertThat(cf.getChannelRpcTimeout(), is(10000));
        assertThat(cf.isChannelShouldCheckRpcResponseType(), is(true));

        assertThat(cf.getNioParams(), notNullValue());
        assertThat(cf.getNioParams().getReadByteBufferSize(), is(32000));
        assertThat(cf.getNioParams().getWriteByteBufferSize(), is(32000));
        assertThat(cf.getNioParams().getNbIoThreads(), is(2));
        assertThat(cf.getNioParams().getWriteEnqueuingTimeoutInMs(), is(5000));
        assertThat(cf.getNioParams().getWriteQueueCapacity(), is(1000));
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
