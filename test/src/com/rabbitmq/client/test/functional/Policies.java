package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.tools.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class Policies extends BrokerTestCase {
    @Override protected void createResources() throws IOException {
        Host.rabbitmqctl("set_policy AE ^has-ae {\\\"alternate-exchange\\\":\\\"ae\\\"}");
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
    }

    // i.e. the argument takes priority over the policy
    public void testAlternateExchangeArgs() throws IOException {
        String q = channel.queueDeclare().getQueue();
        channel.exchangeDeclare("ae2", "fanout", false, true, null);
        channel.queueBind(q, "ae2", "");
        channel.basicPublish("has-ae-args", "", MessageProperties.BASIC, "".getBytes());
        assertDelivered(q, 1);
    }

    @Override protected void releaseResources() throws IOException {
        Host.rabbitmqctl("clear_policy AE");
        channel.exchangeDelete("has-ae");
    }
}
