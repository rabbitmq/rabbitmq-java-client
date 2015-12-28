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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.client.test.server;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.test.AbstractRMQTestSuite;
import com.rabbitmq.client.test.functional.FunctionalTests;
import com.rabbitmq.tools.Host;

public class HATests extends AbstractRMQTestSuite {
    // this is horrific
    public static boolean HA_TESTS_RUNNING = false;
    
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("server-tests");
        if(!isUnderUmbrella()) return suite;
        suite.addTestSuite(SetUp.class);
        FunctionalTests.add(suite);
        ServerTests.add(suite);
        suite.addTestSuite(TearDown.class);
        return suite;
    }

    // This is of course an abuse of the TestCase concept - but I don't want to
    // run this command on every test case. And there's no hook for "before /
    // after this test suite".
    public static class SetUp extends TestCase {
        @Override
        protected void setUp() throws Exception {
            Host.invokeMakeTarget("enable-ha");
            HA_TESTS_RUNNING = true;
        }

        public void testNothing() {}
    }

    public static class TearDown extends TestCase {
        @Override
        protected void tearDown() throws Exception {
            Host.invokeMakeTarget("disable-ha");
            HA_TESTS_RUNNING = false;
        }

        public void testNothing() {}
    }
}
