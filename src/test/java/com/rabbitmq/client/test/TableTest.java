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


package com.rabbitmq.client.test;

import com.rabbitmq.client.impl.*;
import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TableTest
{

    public byte [] marshal(Map<String, Object> table) 
        throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MethodArgumentWriter writer = new MethodArgumentWriter(new ValueWriter(new DataOutputStream(buffer)));
        writer.writeTable(table);
        writer.flush();
        
        assertEquals(Frame.tableSize(table) + 4, buffer.size());
        return buffer.toByteArray();
    }

    public Map<String, Object> unmarshal(byte [] bytes) 
        throws IOException
    {
        MethodArgumentReader reader = 
            new MethodArgumentReader
            (new ValueReader
             (new DataInputStream
              (new ByteArrayInputStream(bytes))));
        
        return reader.readTable();
    }

    public Date secondDate()
    {
        return new Date((System.currentTimeMillis()/1000)*1000);
    }

    @Test public void loop()
        throws IOException
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

        table.put("e", -126);
        assertEquals(table, unmarshal(marshal(table)));
    }
}
