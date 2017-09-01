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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.test.BrokerTestCase;

public class Tables extends BrokerTestCase
{

    @Test public void types() throws IOException {

        Map<String, Object> table = new HashMap<String, Object>();
        Map<String, Object> subTable = new HashMap<String, Object>();
        subTable.put("key", 1);
        table.put("S", LongStringHelper.asLongString("string"));
        table.put("I", Integer.valueOf(1));
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
        List<Object> fieldArray = new ArrayList<Object>();
        fieldArray.add(LongStringHelper.asLongString("foo"));
        fieldArray.add(123);
        table.put("A", fieldArray);
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
        channel.exchangeDeclare("x", "direct", false, false, table);
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
            }
            else if (va instanceof List && vb instanceof List) {
                Iterator<?> vbi = ((List<?>)vb).iterator();
                for (Object vaEntry : (List<?>)va) {
                    Object vbEntry = vbi.next();
                    assertEquals("arrays unequal at key " + k, vaEntry, vbEntry);
                }
            }
            else {
                assertEquals("unequal entry for key " + k, va, vb);
            }
        }
    }

}
