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

        AMQP.Queue.DeclareOk ok = channel.queueDeclare();
        String q = ok.getQueue();

        Map<String, Object> headers = new HashMap<String, Object>();
        Map<String, Object> subTable = new HashMap<String, Object>();
        subTable.put("key", 1);
        headers.put("S", LongStringHelper.asLongString("string"));
        headers.put("I", new Integer(1));
        headers.put("D", new BigDecimal("1.1"));
        headers.put("T", new java.util.Date(1000000));
        headers.put("F", subTable);
        headers.put("b", (byte)1);
        headers.put("d", 1.1d);
        headers.put("f", 1.1f);
        headers.put("l", 1L);
        headers.put("s", (short)1);
        headers.put("t", true);
        headers.put("x", "byte".getBytes());
        headers.put("V", null);
        BasicProperties props = new BasicProperties(null, null, headers, null,
                                                    null, null, null, null,
                                                    null, null, null, null,
                                                    null, null);

        channel.basicPublish("", q, props, "".getBytes());
        BasicProperties rProps = channel.basicGet(q, true).getProps();
        assertMapsEqual(props.headers, rProps.headers);
        //        assertEquals(props.headers, rProps.headers);

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
