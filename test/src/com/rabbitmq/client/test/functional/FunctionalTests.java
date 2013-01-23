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


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.impl.WorkPoolTests;
import com.rabbitmq.client.test.Bug20004Test;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class FunctionalTests extends TestCase {
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("functional");
        add(suite);
        return suite;
    }

    public static void add(TestSuite suite) {
        suite.addTestSuite(ConnectionOpen.class);
        suite.addTestSuite(Heartbeat.class);
        suite.addTestSuite(Tables.class);
        suite.addTestSuite(DoubleDeletion.class);
        suite.addTestSuite(Routing.class);
        suite.addTestSuite(BindingLifecycle.class);
        suite.addTestSuite(Recover.class);
        suite.addTestSuite(Reject.class);
        suite.addTestSuite(Transactions.class);
        suite.addTestSuite(RequeueOnConnectionClose.class);
        suite.addTestSuite(RequeueOnChannelClose.class);
        suite.addTestSuite(DurableOnTransient.class);
        suite.addTestSuite(NoRequeueOnCancel.class);
        suite.addTestSuite(Bug20004Test.class);
        suite.addTestSuite(ExchangeDeleteIfUnused.class);
        suite.addTestSuite(QosTests.class);
        suite.addTestSuite(AlternateExchange.class);
        suite.addTestSuite(ExchangeExchangeBindings.class);
        suite.addTestSuite(ExchangeExchangeBindingsAutoDelete.class);
        suite.addTestSuite(ExchangeDeclare.class);
        suite.addTestSuite(FrameMax.class);
        suite.addTestSuite(QueueLifecycle.class);
        suite.addTestSuite(QueueLease.class);
        suite.addTestSuite(QueueExclusivity.class);
        suite.addTestSuite(InvalidAcks.class);
        suite.addTestSuite(InvalidAcksTx.class);
        suite.addTestSuite(DefaultExchange.class);
        suite.addTestSuite(UnbindAutoDeleteExchange.class);
        suite.addTestSuite(Confirm.class);
        suite.addTestSuite(ConsumerCancelNotificiation.class);
        suite.addTestSuite(UnexpectedFrames.class);
        suite.addTestSuite(PerQueueTTL.class);
        suite.addTestSuite(PerMessageTTL.class);
        suite.addTestSuite(PerQueueVsPerMessageTTL.class);
        suite.addTestSuite(DeadLetterExchange.class);
        suite.addTestSuite(SaslMechanisms.class);
        suite.addTestSuite(UserIDHeader.class);
        suite.addTestSuite(InternalExchange.class);
        suite.addTestSuite(CcRoutes.class);
        suite.addTestSuite(WorkPoolTests.class);
    }
}
