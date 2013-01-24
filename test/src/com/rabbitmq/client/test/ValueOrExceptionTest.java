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

package com.rabbitmq.client.test;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.utility.ValueOrException;
import com.rabbitmq.utility.SensibleClone;


public class ValueOrExceptionTest extends TestCase {
    public static class InsufficientMagicException extends Exception 
      implements SensibleClone<InsufficientMagicException> {
      /** Default for no check. */
        private static final long serialVersionUID = 1L;

    public InsufficientMagicException(String message) {
        super(message);
      }

      public InsufficientMagicException sensibleClone() {
        return new InsufficientMagicException(getMessage());
      }
    }


    public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("valueOrEx");
        suite.addTestSuite(ValueOrExceptionTest.class);
        return suite;
    }

    public void testStoresValue() throws InsufficientMagicException {
        Integer value = new Integer(3);

        ValueOrException<Integer, InsufficientMagicException> valueOrEx = 
            ValueOrException.<Integer, InsufficientMagicException>makeValue(value);
        
        Integer returnedValue = valueOrEx.getValue();
        assertTrue(returnedValue == value);
    }

    public void testClonesException() {
        InsufficientMagicException exception = 
            new InsufficientMagicException("dummy message");
        ValueOrException<Integer, InsufficientMagicException> valueOrEx 
            = ValueOrException.makeException(exception);

        try {
            valueOrEx.getValue();
            fail("Expected exception");
        } catch(InsufficientMagicException returnedException) {
            assertTrue(returnedException != exception);
            assertEquals(returnedException.getMessage(), exception.getMessage());
            boolean inGetValue = false;
            for(StackTraceElement elt : returnedException.getStackTrace())
              inGetValue |= "getValue".equals(elt.getMethodName());
            assertTrue(inGetValue);
        }
    }
}
