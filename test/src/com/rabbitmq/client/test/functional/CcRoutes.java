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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.functional;

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
    protected BasicProperties props;
    protected Map headers;
    protected List ccList;
    protected List bccList;

    @Override protected void setUp() throws IOException {
        super.setUp();
        props = new BasicProperties();
        headers = new HashMap();
        ccList = new ArrayList();
        bccList = new ArrayList();
    }

    @Override protected void createResources() throws IOException {
        super.createResources();
        for (String q : queues) {
            channel.queueDeclare(q, false, false, false, null);
        }
        channel.exchangeDeclare(exDirect, "direct");
        channel.exchangeDeclare(exTopic, "topic");
    }

    @Override protected void releaseResources() throws IOException {
        for (String q : queues) {
            channel.queueDelete(q);
        }
        channel.exchangeDelete(exDirect);
        channel.exchangeDelete(exTopic);
        super.releaseResources();
    }

    public void testCcSingleton() throws IOException {
        headers.put("CC", "queue2");
        props.setHeaders(headers);
        channel.basicPublish("", "queue1", props, new byte[0]);
        expect(new String []{"queue1", "queue2"});
     }

    public void testCcList() throws IOException {
        ccList.add("queue2");
        ccList.add("queue3");
        headerPublish("", "queue1", ccList, null);
        expect(new String []{"queue1", "queue2", "queue3"});
     }

    public void testCcIgnoreEmptyAndInvalidRoutes() throws IOException {
        bccList.add("frob");
        headerPublish("", "queue1", ccList, bccList);
        expect(new String []{"queue1"});
     }

    public void testBcc() throws IOException {
        bccList.add("queue2");
        headerPublish("", "queue1", null, bccList);
        expect(new String []{"queue1", "queue2"});
     }

    public void testNoDuplicates() throws IOException {
        ccList.add("queue1");
        ccList.add("queue1");
        bccList.add("queue1");
        headerPublish("", "queue1", null, bccList);
        expect(new String[] {"queue1"});
     }

    public void testDirectExchangeWithoutBindings() throws IOException {
        ccList.add("queue1");
        headerPublish(exDirect, "queue2", ccList, null);
        expect(new String[] {});
    }

    public void testTopicExchange() throws IOException {
        ccList.add("routing_key");
        channel.queueBind("queue2", exTopic, "routing_key");
        headerPublish(exTopic, "", ccList, null);
        expect(new String[] {"queue2"});
    }

    public void testBoundExchanges() throws IOException {
        ccList.add("routing_key1");
        bccList.add("routing_key2");
        channel.exchangeBind(exTopic, exDirect, "routing_key1");
        channel.queueBind("queue2", exTopic, "routing_key2");
        headerPublish(exDirect, "", ccList, bccList);
        expect(new String[] {"queue2"});
    }

    private void headerPublish(String ex, String to, List cc, List bcc) throws IOException {
        if (cc != null) {
            headers.put("CC", ccList);
        }
        if (bcc != null) {
            headers.put("BCC", bccList);
        }
        props.setHeaders(headers);
        channel.basicPublish(ex, to, props, new byte[0]);
    }

    private void expect(String[] expectedQueues) throws IOException {
        GetResponse getResponse;
        List expectedList = Arrays.asList(expectedQueues);
        for (String q : queues) {
            getResponse = basicGet(q);
            if (expectedList.contains(q)) {
                assertNotNull(getResponse);
                assertEquals(0, getResponse.getMessageCount());
                Map headers = getResponse.getProps().getHeaders();
                if (headers != null)
                    assertFalse(headers.containsKey("BCC"));
            } else {
                assertNull(getResponse);
            }
        }
    }
}
