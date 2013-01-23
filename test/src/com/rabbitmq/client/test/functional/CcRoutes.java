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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CcRoutes extends BrokerTestCase  {

    static private String[] queues = new String[]{"queue1", "queue2", "queue3"};
    protected String exDirect = "direct_cc_exchange";
    protected String exTopic = "topic_cc_exchange";
    protected BasicProperties.Builder propsBuilder;
    protected Map<String, Object> headers;
    protected List<String> ccList;
    protected List<String> bccList;

    @Override protected void setUp() throws IOException {
        super.setUp();
        propsBuilder = new BasicProperties.Builder();
        headers = new HashMap<String, Object>();
        ccList = new ArrayList<String>();
        bccList = new ArrayList<String>();
    }

    @Override protected void createResources() throws IOException {
        super.createResources();
        for (String q : queues) {
            channel.queueDeclare(q, false, true, true, null);
        }
        channel.exchangeDeclare(exDirect, "direct", false, true, null);
        channel.exchangeDeclare(exTopic, "topic", false, true, null);
    }

    public void testCcList() throws IOException {
        ccList.add("queue2");
        ccList.add("queue3");
        headerPublish("", "queue1", ccList, null);
        expect(new String []{"queue1", "queue2", "queue3"}, true);
     }

    public void testCcIgnoreEmptyAndInvalidRoutes() throws IOException {
        bccList.add("frob");
        headerPublish("", "queue1", ccList, bccList);
        expect(new String []{"queue1"}, true);
     }

    public void testBcc() throws IOException {
        bccList.add("queue2");
        headerPublish("", "queue1", null, bccList);
        expect(new String []{"queue1", "queue2"}, false);
     }

    public void testNoDuplicates() throws IOException {
        ccList.add("queue1");
        ccList.add("queue1");
        bccList.add("queue1");
        headerPublish("", "queue1", ccList, bccList);
        expect(new String[] {"queue1"}, true);
     }

    public void testDirectExchangeWithoutBindings() throws IOException {
        ccList.add("queue1");
        headerPublish(exDirect, "queue2", ccList, null);
        expect(new String[] {}, true);
    }

    public void testTopicExchange() throws IOException {
        ccList.add("routing_key");
        channel.queueBind("queue2", exTopic, "routing_key");
        headerPublish(exTopic, "", ccList, null);
        expect(new String[] {"queue2"}, true);
    }

    public void testBoundExchanges() throws IOException {
        ccList.add("routing_key1");
        bccList.add("routing_key2");
        channel.exchangeBind(exTopic, exDirect, "routing_key1");
        channel.queueBind("queue2", exTopic, "routing_key2");
        headerPublish(exDirect, "", ccList, bccList);
        expect(new String[] {"queue2"}, true);
    }

    public void testNonArray() throws IOException {
        headers.put("CC", 0);
        propsBuilder.headers(headers);
        channel.basicPublish("", "queue1", propsBuilder.build(), new byte[0]);
        try {
            expect(new String[] {}, false);
            fail();
        } catch (IOException e) {
            checkShutdownSignal(AMQP.PRECONDITION_FAILED, e);
        }
    }

    private void headerPublish(String ex, String to, List<String> cc, List<String> bcc) throws IOException {
        if (cc != null) {
            headers.put("CC", ccList);
        }
        if (bcc != null) {
            headers.put("BCC", bccList);
        }
        propsBuilder.headers(headers);
        channel.basicPublish(ex, to, propsBuilder.build(), new byte[0]);
    }

    private void expect(String[] expectedQueues, boolean usedCc) throws IOException {
        GetResponse getResponse;
        List<String> expectedList = Arrays.asList(expectedQueues);
        for (String q : queues) {
            getResponse = basicGet(q);
            if (expectedList.contains(q)) {
                assertNotNull(getResponse);
                assertEquals(0, getResponse.getMessageCount());
                Map<?, ?> headers = getResponse.getProps().getHeaders();
                if (headers != null){
                    assertEquals(usedCc, headers.containsKey("CC"));
                    assertFalse(headers.containsKey("BCC"));
                }
            } else {
                assertNull(getResponse);
            }
        }
    }
}
