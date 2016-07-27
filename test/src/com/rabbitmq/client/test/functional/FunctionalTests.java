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
        suite.addTestSuite(QueueSizeLimit.class);
        suite.addTestSuite(InvalidAcks.class);
        suite.addTestSuite(InvalidAcksTx.class);
        suite.addTestSuite(DefaultExchange.class);
        suite.addTestSuite(UnbindAutoDeleteExchange.class);
        suite.addTestSuite(Confirm.class);
        suite.addTestSuite(ConsumerCancelNotification.class);
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
        suite.addTestSuite(HeadersExchangeValidation.class);
        suite.addTestSuite(ConsumerPriorities.class);
        suite.addTestSuite(Policies.class);
        suite.addTestSuite(ConnectionRecovery.class);
        suite.addTestSuite(ExceptionHandling.class);
        suite.addTestSuite(PerConsumerPrefetch.class);
        suite.addTestSuite(DirectReplyTo.class);
    }
}
