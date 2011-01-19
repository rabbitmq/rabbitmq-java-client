//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
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
    protected String exFanout = "fanout_cc_exchange";
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
        channel.exchangeDeclare(exFanout, "fanout");
    }

    @Override protected void releaseResources() throws IOException {
        for (String q : queues) {
            channel.queueDelete(q);
        }
        channel.exchangeDelete(exDirect);
        channel.exchangeDelete(exFanout);
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
        headerPublish("", "queue1", ccList, null);
        expect(new String []{"queue1", "queue2"});
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
        headerPublish(exDirect, "unbound", ccList, null);
        expect(new String[] {"queue1"});
    }

    public void testFanoutExchange() throws IOException {
        ccList.add("queue2");
        headerPublish(exFanout, "queue1", ccList, null);
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
                assertFalse(getResponse.getProps().getHeaders().containsKey("BCC"));
            } else {
                assertNull(getResponse);
            }
        }
    }
}
