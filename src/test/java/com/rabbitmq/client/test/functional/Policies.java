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

package com.rabbitmq.client.test.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.server.HATests;
import com.rabbitmq.tools.Host;

public class Policies extends BrokerTestCase {
    private static final int DELAY = 100; // MILLIS

    @Override protected void createResources() throws IOException {
        setPolicy("AE", "^has-ae", "\"alternate-exchange\":\"ae\"");
        setPolicy("DLX", "^has-dlx", "\"dead-letter-exchange\":\"dlx\",\"dead-letter-routing-key\":\"rk\"");
        setPolicy("TTL", "^has-ttl", "\"message-ttl\":" + DELAY);
        setPolicy("Expires", "^has-expires", "\"expires\":" + DELAY);
        setPolicy("MaxLength", "^has-max-length", "\"max-length\":1");
        channel.exchangeDeclare("has-ae", "fanout");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("alternate-exchange", "ae2");
        channel.exchangeDeclare("has-ae-args", "fanout", false, false, args);
    }

    @Test public void alternateExchange() throws IOException, InterruptedException {
        String q = declareQueue();
        channel.exchangeDeclare("ae", "fanout", false, true, null);
        channel.queueBind(q, "ae", "");
        basicPublishVolatile("has-ae", "");
        assertDelivered(q, 1);
        clearPolicies();

        basicPublishVolatile("has-ae", "");
        assertDelivered(q, 0);
    }

    // i.e. the argument takes priority over the policy
    @Test public void alternateExchangeArgs() throws IOException {
        String q = declareQueue();
        channel.exchangeDeclare("ae2", "fanout", false, true, null);
        channel.queueBind(q, "ae2", "");
        basicPublishVolatile("has-ae-args", "");
        assertDelivered(q, 1);
    }

    @Test public void deadLetterExchange() throws IOException, InterruptedException {
        Map<String, Object> args = ttlArgs(0);
        String src = declareQueue("has-dlx", args);
        String dest = declareQueue();
        channel.exchangeDeclare("dlx", "fanout", false, true, null);
        channel.queueBind(dest, "dlx", "");
        basicPublishVolatile(src);
        Thread.sleep(DELAY);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk", resp.getEnvelope().getRoutingKey());
        clearPolicies();

        basicPublishVolatile(src);
        Thread.sleep(DELAY);
        assertDelivered(dest, 0);
    }

    // again the argument takes priority over the policy
    @Test public void deadLetterExchangeArgs() throws IOException, InterruptedException {
        Map<String, Object> args = ttlArgs(0);
        args.put("x-dead-letter-exchange", "dlx2");
        args.put("x-dead-letter-routing-key", "rk2");
        String src = declareQueue("has-dlx-args", args);
        String dest = declareQueue();
        channel.exchangeDeclare("dlx2", "fanout", false, true, null);
        channel.queueBind(dest, "dlx2", "");
        basicPublishVolatile(src);
        Thread.sleep(DELAY);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk2", resp.getEnvelope().getRoutingKey());
    }

    @Test public void tTL() throws IOException, InterruptedException {
        String q = declareQueue("has-ttl", null);
        basicPublishVolatile(q);
        Thread.sleep(2 * DELAY);
        assertDelivered(q, 0);
        clearPolicies();

        basicPublishVolatile(q);
        Thread.sleep(2 * DELAY);
        assertDelivered(q, 1);
    }

    // Test that we get lower of args and policy
    @Test public void tTLArgs() throws IOException, InterruptedException {
        String q = declareQueue("has-ttl", ttlArgs(3 * DELAY));
        basicPublishVolatile(q);
        Thread.sleep(2 * DELAY);
        assertDelivered(q, 0);
        clearPolicies();

        basicPublishVolatile(q);
        Thread.sleep(2 * DELAY);
        assertDelivered(q, 1);
        basicPublishVolatile(q);
        Thread.sleep(4 * DELAY);
        assertDelivered(q, 0);
    }

    @Test public void expires() throws IOException, InterruptedException {
        String q = declareQueue("has-expires", null);
        Thread.sleep(2 * DELAY);
        assertFalse(queueExists(q));
        clearPolicies();

        q = declareQueue("has-expires", null);
        Thread.sleep(2 * DELAY);
        assertTrue(queueExists(q));
    }

    // Test that we get lower of args and policy
    @Test public void expiresArgs() throws IOException, InterruptedException {
        String q = declareQueue("has-expires", args("x-expires", 3 * DELAY));
        Thread.sleep(2 * DELAY);
        assertFalse(queueExists(q));
        clearPolicies();

        q = declareQueue("has-expires", args("x-expires", 3 * DELAY));
        Thread.sleep(2 * DELAY);
        assertTrue(queueExists(q));
    }

    @Test public void maxLength() throws IOException, InterruptedException {
        String q = declareQueue("has-max-length", null);
        basicPublishVolatileN(q, 3);
        assertDelivered(q, 1);
        clearPolicies();

        basicPublishVolatileN(q, 3);
        assertDelivered(q, 3);
    }

    @Test public void maxLengthArgs() throws IOException, InterruptedException {
        String q = declareQueue("has-max-length", args("x-max-length", 2));
        basicPublishVolatileN(q, 3);
        assertDelivered(q, 1);
        clearPolicies();

        basicPublishVolatileN(q, 3);
        assertDelivered(q, 2);
    }

    @Override protected void releaseResources() throws IOException {
        clearPolicies();
        channel.exchangeDelete("has-ae");
        channel.exchangeDelete("has-ae-args");
    }

    private final Set<String> policies = new HashSet<String>();

    private void setPolicy(String name, String pattern, String definition) throws IOException {
        // We need to override the HA policy that we use in HATests, so
        // priority 1. But we still want a valid test of HA, so add the
        // ha-mode definition.
        if (HATests.HA_TESTS_RUNNING) {
            definition += ",\"ha-mode\":\"all\"";
        }
        Host.rabbitmqctl("set_policy --priority 1 " + name + " " + pattern +
                         " {" + escapeDefinition(definition) + "}");
        policies.add(name);
    }

    private String escapeDefinition(String definition) {
        return definition.replaceAll(",", "\\\\,").replaceAll("\"", "\\\\\\\"");
    }

    private void clearPolicies() throws IOException {
        for (String policy : policies) {
            Host.rabbitmqctl("clear_policy " + policy);
        }
        policies.clear();
    }

    private Map<String, Object> ttlArgs(int ttl) {
        return args("x-message-ttl", ttl);
    }

    private Map<String, Object> args(String name, Object value) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(name, value);
        return args;
    }

    private String declareQueue() throws IOException {
        return channel.queueDeclare().getQueue();
    }

    private String declareQueue(String name, Map<String, Object> args) throws IOException {
        return channel.queueDeclare(name, false, true, false, args).getQueue();
    }

    private boolean queueExists(String name) throws IOException {
        Channel ch2 = connection.createChannel();
        try {
            ch2.queueDeclarePassive(name);
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }

    private void basicPublishVolatileN(String q, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            basicPublishVolatile(q);
        }
    }

}
