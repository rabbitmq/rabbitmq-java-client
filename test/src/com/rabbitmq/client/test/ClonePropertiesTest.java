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

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.MessageProperties;

/**
 * Tests for cloning of {@link MessageProperties}
 * <p/>
 * (Development note: Is this really necessary? With builders, properties can be 'copied' by creating a (mutable)
 * builder from a Properties object, updating it, and then building another Properties object. This leaves the way open
 * for us to make Properties objects immutable in the future.)
 */
public class ClonePropertiesTest extends TestCase {

    /**
     * @return test suite for {@link MessageProperties}s cloning.
     */
    public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("cloneProperties");
        suite.addTestSuite(ClonePropertiesTest.class);
        return suite;
    }

    /**
     * Clones are <i>not</i> identical objects (only a trivial test)
     * @throws Exception test failure
     */
    public void testPropertyCloneIsDistinct() throws Exception
    {
        assertTrue(MessageProperties.MINIMAL_PERSISTENT_BASIC !=
                   MessageProperties.MINIMAL_PERSISTENT_BASIC.clone());
    }

    /**
     * Clones have the same values
     * @throws Exception test failure
     */
    public void testPropertyClonePreservesValues() throws Exception
    {
        assertEquals(MessageProperties.MINIMAL_PERSISTENT_BASIC.getDeliveryMode(),
                     ((BasicProperties) MessageProperties.MINIMAL_PERSISTENT_BASIC.clone())
                       .getDeliveryMode());
        assertEquals(new Integer(2),
                     ((BasicProperties) MessageProperties.MINIMAL_PERSISTENT_BASIC.clone())
                       .getDeliveryMode());
    }
}
