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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.ConnectionFactory;

public class AmqpUriTest
{
    @Test public void uriParsing()
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        /* From the spec (subset of the tests) */
        parseSuccess("amqp://user:pass@host:10000/vhost",
                     "user", "pass", "host", 10000, "vhost", false);
        parseSuccess("aMQps://user%61:%61pass@host:10000/v%2fhost",
                     "usera", "apass", "host", 10000, "v/host", true);
        parseSuccess("amqp://host", "guest", "guest", "host", 5672, "/", false);
        parseSuccess("amqp:///vhost",
                     "guest", "guest", "localhost", 5672, "vhost", false);
        parseSuccess("amqp://host/", "guest", "guest", "host", 5672, "", false);
        parseSuccess("amqp://host/%2f", "guest", "guest", "host", 5672, "/", false);
        parseSuccess("amqp://[::1]", "guest", "guest", "[::1]", 5672, "/", false);

        /* Various other success cases */
        parseSuccess("amqp://host:100", "guest", "guest", "host", 100, "/", false);
        parseSuccess("amqp://[::1]:100", "guest", "guest", "[::1]", 100, "/", false);

        parseSuccess("amqp://host/blah",
                     "guest", "guest", "host", 5672, "blah", false);
        parseSuccess("amqp://host:100/blah",
                     "guest", "guest", "host", 100, "blah", false);
        parseSuccess("amqp://[::1]/blah",
                     "guest", "guest", "[::1]", 5672, "blah", false);
        parseSuccess("amqp://[::1]:100/blah",
                     "guest", "guest", "[::1]", 100, "blah", false);

        parseSuccess("amqp://user:pass@host",
                     "user", "pass", "host", 5672, "/", false);
        parseSuccess("amqp://user:pass@[::1]",
                     "user", "pass", "[::1]", 5672, "/", false);
        parseSuccess("amqp://user:pass@[::1]:100",
                     "user", "pass", "[::1]", 100, "/", false);

        /* using query parameters */
        parseSuccess("amqp://user:pass@host:10000/vhost?",
                     "user", "pass", "host", 10000, "vhost", false);
        parseSuccess("amqp://user:pass@host:10000/vhost?&",
                     "user", "pass", "host", 10000, "vhost", false);
        parseSuccess("amqp://user:pass@host:10000/vhost?unknown_parameter",
                     "user", "pass", "host", 10000, "vhost", false);
        parseSuccess("amqp://user:pass@host:10000/vhost?unknown_parameter=value",
                     "user", "pass", "host", 10000, "vhost", false);
        parseSuccess("amqp://user:pass@host:10000/vhost?unknown%2fparameter=value",
                     "user", "pass", "host", 10000, "vhost", false);

        parseSuccess("amqp://user:pass@host:10000/vhost?heartbeat=342",
                     "user", "pass", "host", 10000, "vhost", false,
                     342, null, null);
        parseSuccess("amqp://user:pass@host:10000/vhost?connection_timeout=442",
                     "user", "pass", "host", 10000, "vhost", false,
                     null, 442, null);
        parseSuccess("amqp://user:pass@host:10000/vhost?channel_max=542",
                     "user", "pass", "host", 10000, "vhost", false,
                     null, null, 542);
        parseSuccess("amqp://user:pass@host:10000/vhost?heartbeat=342&connection_timeout=442&channel_max=542",
                     "user", "pass", "host", 10000, "vhost", false,
                     342, 442, 542);
        parseSuccess("amqp://user:pass@host:10000/vhost?heartbeat=342&connection_timeout=442&channel_max=542&a=b",
                     "user", "pass", "host", 10000, "vhost", false,
                     342, 442, 542);

        /* Various failure cases */
        parseFail("https://www.rabbitmq.com");
        parseFail("amqp://foo[::1]");
        parseFail("amqp://foo:[::1]");
        parseFail("amqp://[::1]foo");

        parseFail("amqp://foo%1");
        parseFail("amqp://foo%1x");
        parseFail("amqp://foo%xy");

        parseFail("amqp://user:pass@host:10000/vhost?heartbeat=not_an_integer");
        parseFail("amqp://user:pass@host:10000/vhost?heartbeat=-1");
        parseFail("amqp://user:pass@host:10000/vhost?connection_timeout=not_an_integer");
        parseFail("amqp://user:pass@host:10000/vhost?connection_timeout=-1");
        parseFail("amqp://user:pass@host:10000/vhost?channel_max=not_an_integer");
        parseFail("amqp://user:pass@host:10000/vhost?channel_max=-1");
        parseFail("amqp://user:pass@host:10000/vhost?heartbeat=342?connection_timeout=442");
    }

    @Test
    public void processUriQueryParameterShouldBeCalledForNotHandledParameter() throws Exception {
        Map<String, String> processedParameters = new HashMap<>();
        ConnectionFactory cf = new ConnectionFactory() {
            @Override
            protected void processUriQueryParameter(String key, String value) {
                processedParameters.put(key, value);
            }
        };
        cf.setUri("amqp://user:pass@host:10000/vhost?heartbeat=60&key=value");
        assertThat(processedParameters).hasSize(1).containsEntry("key", "value");
    }

    private void parseSuccess(String uri, String user, String password,
                              String host, int port, String vhost, boolean secured)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        parseSuccess(uri, user, password, host, port, vhost, secured, null, null, null);
    }

    private void parseSuccess(String uri, String user, String password,
                              String host, int port, String vhost, boolean secured,
                              Integer heartbeat, Integer connectionTimeout, Integer channelMax)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setUri(uri);

        assertEquals(user, cf.getUsername());
        assertEquals(password, cf.getPassword());
        assertEquals(host, cf.getHost());
        assertEquals(port, cf.getPort());
        assertEquals(vhost, cf.getVirtualHost());
        assertEquals(secured, cf.isSSL());
        
        if(heartbeat != null) {
            assertEquals(heartbeat.intValue(), cf.getRequestedHeartbeat());
        }
        if(connectionTimeout != null) {
            assertEquals(connectionTimeout.intValue(), cf.getConnectionTimeout());
        }
        if(channelMax != null) {
            assertEquals(channelMax.intValue(), cf.getRequestedChannelMax());
        }
    }

    private void parseFail(String uri) {
        try {
            (TestUtils.connectionFactory()).setUri(uri);
            fail("URI parse didn't fail: '" + uri + "'");
        } catch (Exception e) {
            // whoosh!
        }
    }
}
