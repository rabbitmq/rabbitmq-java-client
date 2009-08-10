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
package com.rabbitmq.client.test;

import java.io.IOException;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.utility.ValueOrException;


public class ValueOrExceptionTest extends TestCase {
    public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("valueOrEx");
        suite.addTestSuite(ValueOrExceptionTest.class);
        return suite;
    }

    public void testStoresValue() throws IOException {
        Integer value = new Integer(3);
        ValueOrException<Integer, IOException> valueOrEx = ValueOrException.<Integer, IOException>makeValue(value);
        
        Integer returnedValue = valueOrEx.getValue();
        assertTrue(returnedValue == value);
    }

    public void testStoresException() {
        IOException exception = new IOException("dummy message");
        ValueOrException<Integer, IOException> valueOrEx = ValueOrException.<Integer, IOException>makeException(exception);

        try {
            valueOrEx.getValue();
            fail("Expected exception");
        } catch(IOException returnedException) {
            assertTrue(returnedException == exception);
        }
    }
}
