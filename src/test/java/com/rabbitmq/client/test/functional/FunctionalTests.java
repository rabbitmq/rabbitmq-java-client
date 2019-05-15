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
import com.rabbitmq.client.test.AbstractRMQTestSuite;
import com.rabbitmq.client.test.Bug20004Test;
import com.rabbitmq.client.test.RequiredPropertiesSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(RequiredPropertiesSuite.class)
@Suite.SuiteClasses({
	ConnectionOpen.class,
	Heartbeat.class,
	Tables.class,
	DoubleDeletion.class,
	Routing.class,
	BindingLifecycle.class,
	Recover.class,
	Reject.class,
	Transactions.class,
	RequeueOnConnectionClose.class,
	RequeueOnChannelClose.class,
	DurableOnTransient.class,
    NoRequeueOnCancel.class,
    Bug20004Test.class,
    ExchangeDeleteIfUnused.class,
    //QosTests.class,
    AlternateExchange.class,
    ExchangeExchangeBindings.class,
    ExchangeExchangeBindingsAutoDelete.class,
    ExchangeDeclare.class,
    FrameMax.class,
    QueueLifecycle.class,
    QueueLease.class,
    QueueExclusivity.class,
    QueueSizeLimit.class,
    InvalidAcks.class,
    InvalidAcksTx.class,
    DefaultExchange.class,
    UnbindAutoDeleteExchange.class,
    Confirm.class,
    ConsumerCancelNotification.class,
    UnexpectedFrames.class,
    PerQueueTTL.class,
    PerMessageTTL.class,
    PerQueueVsPerMessageTTL.class,
    DeadLetterExchange.class,
    SaslMechanisms.class,
    UserIDHeader.class,
    InternalExchange.class,
    CcRoutes.class,
    WorkPoolTests.class,
    HeadersExchangeValidation.class,
    ConsumerPriorities.class,
    Policies.class,
    ConnectionRecovery.class,
    ExceptionHandling.class,
    PerConsumerPrefetch.class,
    DirectReplyTo.class,
	ConsumerCount.class,
	BasicGet.class,
	Nack.class,
	ExceptionMessages.class,
	Metrics.class,
	TopologyRecoveryFiltering.class,
	TopologyRecoveryRetry.class
})
public class FunctionalTests {

	// initialize system properties
	static{
		new AbstractRMQTestSuite(){};
	}

}
