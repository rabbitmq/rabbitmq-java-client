package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class Policies extends BrokerTestCase {
    @Override protected void createResources() throws IOException {
        setPolicy("AE", "^has-ae", "{\\\"alternate-exchange\\\":\\\"ae\\\"}");
        setPolicy("DLX", "^has-dlx", "{\\\"dead-letter-exchange\\\":\\\"dlx\\\"\\,\\\"dead-letter-routing-key\\\":\\\"rk\\\"}");
        setPolicy("TTL", "^has-ttl", "{\\\"message-ttl\\\":10}");
        setPolicy("Expires", "^has-expires", "{\\\"expires\\\":10}");
        setPolicy("MaxLength", "^has-max-length", "{\\\"max-length\\\":1}");
        channel.exchangeDeclare("has-ae", "fanout");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("alternate-exchange", "ae2");
        channel.exchangeDeclare("has-ae-args", "fanout", false, false, args);
    }

    public void testAlternateExchange() throws IOException {
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
    public void testAlternateExchangeArgs() throws IOException {
        String q = declareQueue();
        channel.exchangeDeclare("ae2", "fanout", false, true, null);
        channel.queueBind(q, "ae2", "");
        basicPublishVolatile("has-ae-args", "");
        assertDelivered(q, 1);
    }

    public void testDeadLetterExchange() throws IOException, InterruptedException {
        Map<String, Object> args = ttlArgs(0);
        String src = declareQueue("has-dlx", args);
        String dest = declareQueue();
        channel.exchangeDeclare("dlx", "fanout", false, true, null);
        channel.queueBind(dest, "dlx", "");
        basicPublishVolatile(src);
        Thread.sleep(10);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk", resp.getEnvelope().getRoutingKey());
        clearPolicies();

        basicPublishVolatile(src);
        Thread.sleep(10);
        assertDelivered(dest, 0);
    }

    // again the argument takes priority over the policy
    public void testDeadLetterExchangeArgs() throws IOException, InterruptedException {
        Map<String, Object> args = ttlArgs(0);
        args.put("x-dead-letter-exchange", "dlx2");
        args.put("x-dead-letter-routing-key", "rk2");
        String src = declareQueue("has-dlx-args", args);
        String dest = declareQueue();
        channel.exchangeDeclare("dlx2", "fanout", false, true, null);
        channel.queueBind(dest, "dlx2", "");
        basicPublishVolatile(src);
        Thread.sleep(10);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk2", resp.getEnvelope().getRoutingKey());
    }

    public void testTTL() throws IOException, InterruptedException {
        String q = declareQueue("has-ttl", null);
        basicPublishVolatile(q);
        Thread.sleep(20);
        assertDelivered(q, 0);
        clearPolicies();

        basicPublishVolatile(q);
        Thread.sleep(20);
        assertDelivered(q, 1);
    }

    // Test that we get lower of args and policy
    public void testTTLArgs() throws IOException, InterruptedException {
        String q = declareQueue("has-ttl", ttlArgs(30));
        basicPublishVolatile(q);
        Thread.sleep(20);
        assertDelivered(q, 0);
        clearPolicies();

        basicPublishVolatile(q);
        Thread.sleep(20);
        assertDelivered(q, 1);
        basicPublishVolatile(q);
        Thread.sleep(40);
        assertDelivered(q, 0);
    }

    public void testExpires() throws IOException, InterruptedException {
        String q = declareQueue("has-expires", null);
        Thread.sleep(20);
        assertFalse(queueExists(q));
        clearPolicies();

        q = declareQueue("has-expires", null);
        Thread.sleep(20);
        assertTrue(queueExists(q));
    }

    // Test that we get lower of args and policy
    public void testExpiresArgs() throws IOException, InterruptedException {
        String q = declareQueue("has-expires", args("x-expires", 30));
        Thread.sleep(20);
        assertFalse(queueExists(q));
        clearPolicies();

        q = declareQueue("has-expires", args("x-expires", 30));
        Thread.sleep(20);
        assertTrue(queueExists(q));
    }

    public void testMaxLength() throws IOException, InterruptedException {
        String q = declareQueue("has-max-length", null);
        basicPublishVolatileN(q, 3);
        assertDelivered(q, 1);
        clearPolicies();

        basicPublishVolatileN(q, 3);
        assertDelivered(q, 3);
    }

    public void testMaxLengthArgs() throws IOException, InterruptedException {
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

    private Set<String> policies = new HashSet<String>();

    private void setPolicy(String name, String pattern, String definition) throws IOException {
        Host.rabbitmqctl("set_policy " + name + " " + pattern + " " + definition);
        policies.add(name);
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