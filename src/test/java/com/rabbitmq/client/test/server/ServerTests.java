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

import junit.framework.TestSuite;

import com.rabbitmq.client.test.AbstractRMQTestSuite;

public class ServerTests extends AbstractRMQTestSuite {
  public static TestSuite suite() {
        TestSuite suite = new TestSuite("server-tests");
        if(!isUnderUmbrella()) return suite;
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
