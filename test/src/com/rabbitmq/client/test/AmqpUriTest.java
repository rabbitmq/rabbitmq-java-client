//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2011-2013 VMware, Inc.  All rights reserved.
//
//
package com.rabbitmq.client.test;

import com.rabbitmq.client.ConnectionFactory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.net.URISyntaxException;

public class AmqpUriTest extends BrokerTestCase
{
    public void testUriParsing()
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
        parseFail("http://www.rabbitmq.com");
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
        ConnectionFactory cf = new ConnectionFactory();
        cf.setUri(uri);

        assertEquals(user, cf.getUsername());
        assertEquals(password, cf.getPassword());
        assertEquals(host, cf.getHost());
        assertEquals(port, cf.getPort());
        assertEquals(vhost, cf.getVirtualHost());
    }

    private void parseFail(String uri) {
        try {
            (new ConnectionFactory()).setUri(uri);
            fail("URI parse didn't fail: '" + uri + "'");
        } catch (Exception e) {
            // whoosh!
        }
    }
}
