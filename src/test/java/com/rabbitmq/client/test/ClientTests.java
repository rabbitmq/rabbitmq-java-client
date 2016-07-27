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

import junit.framework.TestSuite;

public class ClientTests extends AbstractRMQTestSuite {

  public static TestSuite suite() {
        TestSuite suite = new TestSuite("client");
        suite.addTest(TableTest.suite());
        suite.addTest(LongStringTest.suite());
        suite.addTest(BlockingCellTest.suite());
        suite.addTest(TruncatedInputStreamTest.suite());
        suite.addTest(AMQConnectionTest.suite());
        suite.addTest(ValueOrExceptionTest.suite());
        suite.addTest(BrokenFramesTest.suite());
        suite.addTest(ClonePropertiesTest.suite());
        suite.addTestSuite(Bug20004Test.class);
        suite.addTestSuite(CloseInMainLoop.class);
        suite.addTestSuite(ChannelNumberAllocationTests.class);
        suite.addTestSuite(QueueingConsumerShutdownTests.class);
        suite.addTestSuite(MultiThreadedChannel.class);
        suite.addTestSuite(com.rabbitmq.utility.IntAllocatorTests.class);
        suite.addTestSuite(AMQBuilderApiTest.class);
        suite.addTestSuite(AmqpUriTest.class);
        suite.addTestSuite(JSONReadWriteTest.class);
        suite.addTestSuite(SharedThreadPoolTest.class);
        return suite;
    }
}
