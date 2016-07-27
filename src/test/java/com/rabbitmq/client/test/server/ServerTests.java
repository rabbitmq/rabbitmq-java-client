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

import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.rabbitmq.client.test.AbstractRMQTestSuite;

public class ServerTests extends AbstractRMQTestSuite {

  public static TestSuite suite() {
        TestSuite suite = new TestSuite("server-tests");
        if (!requiredProperties()) return suite;
        add(suite);
        return suite;
    }

  public static void add(TestSuite suite) {
    suite.addTestSuite(Permissions.class);
    suite.addTestSuite(DurableBindingLifecycle.class);
    suite.addTestSuite(DeadLetterExchangeDurable.class);
    suite.addTestSuite(EffectVisibilityCrossNodeTest.class);
    suite.addTestSuite(ExclusiveQueueDurability.class);
    suite.addTestSuite(AbsentQueue.class);
    suite.addTestSuite(AlternateExchangeEquivalence.class);
    suite.addTestSuite(MemoryAlarms.class);
    suite.addTestSuite(MessageRecovery.class);
    suite.addTestSuite(Firehose.class);
    suite.addTestSuite(PersistenceGuarantees.class);
    suite.addTestSuite(Shutdown.class);
    suite.addTestSuite(BlockedConnection.class);
    suite.addTestSuite(ChannelLimitNegotiation.class);
    suite.addTestSuite(LoopbackUsers.class);
  }
}
