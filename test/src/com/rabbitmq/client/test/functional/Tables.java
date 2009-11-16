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
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.AMQP.BasicProperties;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.math.BigDecimal;

public class Tables extends BrokerTestCase
{

    public void testTypes() throws IOException {

        Map<String, Object> table = new HashMap<String, Object>();
        Map<String, Object> subTable = new HashMap<String, Object>();
        subTable.put("key", 1);
        table.put("S", LongStringHelper.asLongString("string"));
        table.put("I", new Integer(1));
        table.put("D", new BigDecimal("1.1"));
        table.put("T", new java.util.Date(1000000));
        table.put("F", subTable);
        table.put("b", (byte)1);
        table.put("d", 1.1d);
        table.put("f", 1.1f);
        table.put("l", 1L);
        table.put("s", (short)1);
        table.put("t", true);
        table.put("x", "byte".getBytes());
        table.put("V", null);

        //roundtrip of content headers
        AMQP.Queue.DeclareOk ok = channel.queueDeclare();
        String q = ok.getQueue();
        BasicProperties props = new BasicProperties(null, null, table, null,
                                                    null, null, null, null,
                                                    null, null, null, null,
                                                    null, null);
        channel.basicPublish("", q, props, "".getBytes());
        BasicProperties rProps = channel.basicGet(q, true).getProps();
        assertMapsEqual(props.getHeaders(), rProps.getHeaders());

        //sending as part of method arguments - we are relying on
        //exchange.declare ignoring the arguments table.
        channel.exchangeDeclare("x", "direct", false, false, false, table);
        channel.exchangeDelete("x");
    }

    private static void assertMapsEqual(Map<String, Object> a,
                                        Map<String, Object> b) {

        assertEquals(a.keySet(), b.keySet());
        Set<String> keys = a.keySet();
        for (String k : keys) {
            Object va = a.get(k);
            Object vb = b.get(k);
            if (va instanceof byte[] && vb instanceof byte[]) {
                assertTrue("unequal entry for key " + k,
                           Arrays.equals((byte[])va, (byte[])vb));
            } else {
                assertEquals("unequal entry for key " + k, va, vb);
            }
        }
    }

}
