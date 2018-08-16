// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client;

import com.rabbitmq.tools.jsonrpc.JacksonJsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcException;
import com.rabbitmq.tools.jsonrpc.JsonRpcMapper;
import org.junit.Test;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JacksonJsonRpcTest extends AbstractJsonRpcTest {

    @Override
    JsonRpcMapper createMapper() {
        return new JacksonJsonRpcMapper();
    }

    @Test
    public void rpc() {
        assertFalse(service.procedurePrimitiveBoolean(true));
        assertFalse(service.procedureBoolean(Boolean.TRUE).booleanValue());
        assertEquals("hello1", service.procedureString("hello"));
        assertEquals("hello1hello2", service.procedureStringString("hello1", "hello2"));
        assertEquals(2, service.procedureInteger(1).intValue());
        assertEquals(2, service.procedurePrimitiveInteger(1));
        assertEquals(2, service.procedureDouble(1.0).intValue());
        assertEquals(2, (int) service.procedurePrimitiveDouble(1.0));
        assertEquals(2, (int) service.procedureLongToInteger(1L));
        assertEquals(2, service.procedurePrimitiveLongToInteger(1L));
        assertEquals(2, service.procedurePrimitiveLong(1L));
        assertEquals(2, service.procedureLong(1L).longValue());
        assertEquals("123", service.procedureIntegerToPojo(123).getStringProperty());
        service.procedureNoArgumentVoid();

        Calendar calendar = Calendar.getInstance();
        Date date = calendar.getTime();
        Date returnedDate = service.procedureDateDate(date);
        assertEquals(date.getTime(), returnedDate.getTime());

        Pojo pojo = new Pojo();
        pojo.setStringProperty("hello");
        assertEquals("hello", service.procedurePojoToString(pojo));

        try {
            service.procedureException();
            fail("Remote procedure throwing exception, an exception should have been thrown");
        } catch (UndeclaredThrowableException e) {
            assertTrue(e.getCause() instanceof JsonRpcException);
        }
    }
}
