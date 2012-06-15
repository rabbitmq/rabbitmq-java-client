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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.LongStringHelper;
import com.rabbitmq.client.impl.MethodArgumentReader;
import com.rabbitmq.client.impl.MethodArgumentWriter;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;

/**
 * Unit tests for AMQP wire-protocol tables
 */
public class TableTest extends TestCase
{
    /**
     * @return suite of tests for table testing
     */
    public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("tables");
        suite.addTestSuite(TableTest.class);
        return suite;
    }

    /**
     * Pack a map into an AMQP wire-protocol table
     * @param table map to pack up
     * @return sequence of bytes which is AMQP wire-protocol representation of table
     * @throws Exception test failure
     */
    private byte [] marshal(Map<String, Object> table) throws Exception
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(new DataOutputStream(buffer)));
        writer.writeTable(table);
        writer.flush();

        assertEquals(Frame.tableSize(table) + 4, buffer.size());
        return buffer.toByteArray();
    }

    /**
     * Unpack a table (as a sequence of bytes) into a map
     * @param bytes sequence to unpack
     * @return map constructed from bytes
     * @throws Exception test failure
     */
    private Map<String, Object> unmarshal(byte [] bytes) throws Exception
    {
        MethodArgumentReader reader =
            new MethodArgumentReader
            (new ValueReader
             (new DataInputStream
              (new ByteArrayInputStream(bytes))));

        return reader.readTable();
    }

    private Date secondDate()
    {
        return new Date((System.currentTimeMillis()/1000)*1000);
    }

    /**
     * Check <code>marshal</code> and <code>unmarshal</code> are section and
     * retraction, that is:
     * <br/>
     * <i>unmarshal</i> o <i>marshal</i> = 1<sub>ValidMaps</sub>
     * <br/>
     * on certain trivial examples.
     * <p/>
     * TODO: (Harder) add tests to check that:
     * <br/>
     * <i>marshal</i> o <i>unmarshal</i> = 1<sub>ValidTables</sub>
     * @throws Exception test failure
     */
    public void testLoop() throws Exception
    {
        Map<String, Object> table = new HashMap<String, Object>();
        table.put("a", 1);
        assertEquals(table, unmarshal(marshal(table)));

        table.put("b", secondDate());
        assertEquals(table, unmarshal(marshal(table)));

        table.put("c", new BigDecimal("1.1"));
        assertEquals(table, unmarshal(marshal(table)));

        table.put("d", LongStringHelper.asLongString("d"));
        assertEquals(table, unmarshal(marshal(table)));

    }
}
