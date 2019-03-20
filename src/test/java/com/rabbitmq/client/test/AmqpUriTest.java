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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import com.rabbitmq.client.ConnectionFactory;

public class AmqpUriTest extends BrokerTestCase
{
    @Test public void uriParsing()
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        /* From the spec (subset of the tests) */
        parseSuccess("amqp://user:pass@host:10000/vhost",
                     "user", "pass", "host", 10000, "vhost");
        parseSuccess("aMQps://user%61:%61pass@host:10000/v%2fhost",
                     "usera", "apass", "host", 10000, "v/host");
        parseSuccess("amqp://host", "guest", "guest", "host", 5672, "/");
        parseSuccess("amqp:///vhost",
                     "guest", "guest", "localhost", 5672, "vhost");
        parseSuccess("amqp://host/", "guest", "guest", "host", 5672, "");
        parseSuccess("amqp://host/%2f", "guest", "guest", "host", 5672, "/");
        parseSuccess("amqp://[::1]", "guest", "guest", "[::1]", 5672, "/");

        /* Various other success cases */
        parseSuccess("amqp://host:100", "guest", "guest", "host", 100, "/");
        parseSuccess("amqp://[::1]:100", "guest", "guest", "[::1]", 100, "/");

        parseSuccess("amqp://host/blah",
                     "guest", "guest", "host", 5672, "blah");
        parseSuccess("amqp://host:100/blah",
                     "guest", "guest", "host", 100, "blah");
        parseSuccess("amqp://[::1]/blah",
                     "guest", "guest", "[::1]", 5672, "blah");
        parseSuccess("amqp://[::1]:100/blah",
                     "guest", "guest", "[::1]", 100, "blah");

        parseSuccess("amqp://user:pass@host",
                     "user", "pass", "host", 5672, "/");
        parseSuccess("amqp://user:pass@[::1]",
                     "user", "pass", "[::1]", 5672, "/");
        parseSuccess("amqp://user:pass@[::1]:100",
                     "user", "pass", "[::1]", 100, "/");

        /* Various failure cases */
        parseFail("https://www.rabbitmq.com");
        parseFail("amqp://foo[::1]");
        parseFail("amqp://foo:[::1]");
        parseFail("amqp://[::1]foo");

        parseFail("amqp://foo%1");
        parseFail("amqp://foo%1x");
        parseFail("amqp://foo%xy");
    }

    private void parseSuccess(String uri, String user, String password,
                              String host, int port, String vhost)
        throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException
    {
        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setUri(uri);

        assertEquals(user, cf.getUsername());
        assertEquals(password, cf.getPassword());
        assertEquals(host, cf.getHost());
        assertEquals(port, cf.getPort());
        assertEquals(vhost, cf.getVirtualHost());
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
