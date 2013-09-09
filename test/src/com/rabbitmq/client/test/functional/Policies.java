package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
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
        setAE();
        setDLX();
        channel.exchangeDeclare("has-ae", "fanout");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("alternate-exchange", "ae2");
        channel.exchangeDeclare("has-ae-args", "fanout", false, false, args);
    }

    public void testAlternateExchange() throws IOException {
        String q = channel.queueDeclare().getQueue();
        channel.exchangeDeclare("ae", "fanout", false, true, null);
        channel.queueBind(q, "ae", "");
        channel.basicPublish("has-ae", "", MessageProperties.BASIC, "".getBytes());
        assertDelivered(q, 1);
        clearPolicies();
        channel.basicPublish("has-ae", "", MessageProperties.BASIC, "".getBytes());
        assertDelivered(q, 0);
    }

    // i.e. the argument takes priority over the policy
    public void testAlternateExchangeArgs() throws IOException {
        String q = channel.queueDeclare().getQueue();
        channel.exchangeDeclare("ae2", "fanout", false, true, null);
        channel.queueBind(q, "ae2", "");
        channel.basicPublish("has-ae-args", "", MessageProperties.BASIC, "".getBytes());
        assertDelivered(q, 1);
    }

    public void testDeadLetterExchange() throws IOException, InterruptedException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 0);
        String src = channel.queueDeclare("has-dlx", false, true, false, args).getQueue();
        String dest = channel.queueDeclare().getQueue();
        channel.exchangeDeclare("dlx", "fanout", false, true, null);
        channel.queueBind(dest, "dlx", "");
        channel.basicPublish("", src, MessageProperties.BASIC, "".getBytes());
        Thread.sleep(10);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk", resp.getEnvelope().getRoutingKey());
        clearPolicies();
        channel.basicPublish("", src, MessageProperties.BASIC, "".getBytes());
        Thread.sleep(10);
        assertDelivered(dest, 0);
    }

    // again the argument takes priority over the policy
    public void testDeadLetterExchangeArgs() throws IOException, InterruptedException {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 0);
        args.put("x-dead-letter-exchange", "dlx2");
        args.put("x-dead-letter-routing-key", "rk2");
        String src = channel.queueDeclare("has-dlx-args", false, true, false, args).getQueue();
        String dest = channel.queueDeclare().getQueue();
        channel.exchangeDeclare("dlx2", "fanout", false, true, null);
        channel.queueBind(dest, "dlx2", "");
        channel.basicPublish("", src, MessageProperties.BASIC, "".getBytes());
        Thread.sleep(10);
        GetResponse resp = channel.basicGet(dest, true);
        assertEquals("rk2", resp.getEnvelope().getRoutingKey());
    }

    @Override protected void releaseResources() throws IOException {
        clearPolicies();
        channel.exchangeDelete("has-ae");
        channel.exchangeDelete("has-ae-args");
    }

    private Set<String> policies = new HashSet<String>();

    private void setAE() throws IOException {
        setPolicy("AE", "^has-ae", "{\\\"alternate-exchange\\\":\\\"ae\\\"}");
    }

    private void setDLX() throws IOException {
        setPolicy("DLX", "^has-dlx", "{\\\"dead-letter-exchange\\\":\\\"dlx\\\"\\,\\\"dead-letter-routing-key\\\":\\\"rk\\\"}");
    }

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
}
