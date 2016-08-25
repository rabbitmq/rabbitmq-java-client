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

package com.rabbitmq.client.test.server;

import com.rabbitmq.client.test.AbstractRMQTestSuite;
import com.rabbitmq.client.test.RequiredPropertiesSuite;
import com.rabbitmq.client.test.functional.FunctionalTests;
import com.rabbitmq.tools.Host;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(RequiredPropertiesSuite.class)
@Suite.SuiteClasses({
    HATests.SetUp.class,
    FunctionalTests.class,
    ServerTests.class,
    HATests.TearDown.class
})
public class HATests {

    // initialize system properties
    static{
        new AbstractRMQTestSuite(){};
    }

    // this is horrific
    public static boolean HA_TESTS_RUNNING = false;

    // This is of course an abuse of the TestCase concept - but I don't want to
    // run this command on every test case. And there's no hook for "before /
    // after this test suite".
    public static class SetUp {

        @Test public void setUp() throws Exception {
            Host.rabbitmqctl("set_policy HA '.*' '{\"ha-mode\":\"all\"}'");
            HA_TESTS_RUNNING = true;
        }

        @Test public void testNothing() {}
    }

    public static class TearDown {

        @Test public void tearDown() throws Exception {
            Host.rabbitmqctl("clear_policy HA");
            HA_TESTS_RUNNING = false;
        }

        @Test public void testNothing() {}
    }
}
