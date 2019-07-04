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

import com.rabbitmq.client.JacksonJsonRpcTest;
import com.rabbitmq.client.impl.*;
import com.rabbitmq.utility.IntAllocatorTests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    TableTest.class,
    LongStringTest.class,
    BlockingCellTest.class,
    TruncatedInputStreamTest.class,
    AMQConnectionTest.class,
    AMQChannelTest.class,
    ChannelRpcTimeoutIntegrationTest.class,
    ValueOrExceptionTest.class,
    BrokenFramesTest.class,
    ClonePropertiesTest.class,
    Bug20004Test.class,
    CloseInMainLoop.class,
    ChannelNumberAllocationTests.class,
    QueueingConsumerTests.class,
    MultiThreadedChannel.class,
    IntAllocatorTests.class,
    AMQBuilderApiTest.class,
    AmqpUriTest.class,
    SharedThreadPoolTest.class,
    DnsRecordIpAddressResolverTests.class,
    MetricsCollectorTest.class,
    MicrometerMetricsCollectorTest.class,
    DnsSrvRecordAddressResolverTest.class,
    JavaNioTest.class,
    ConnectionFactoryTest.class,
    RecoveryAwareAMQConnectionFactoryTest.class,
    RpcTest.class,
    SslContextFactoryTest.class,
    LambdaCallbackTest.class,
    ChannelAsyncCompletableFutureTest.class,
    RecoveryDelayHandlerTest.class,
    FrameBuilderTest.class,
    PropertyFileInitialisationTest.class,
    ClientVersionTest.class,
    TestUtilsTest.class,
    StrictExceptionHandlerTest.class,
    NoAutoRecoveryWhenTcpWindowIsFullTest.class,
    JacksonJsonRpcTest.class,
    AddressTest.class,
    DefaultRetryHandlerTest.class,
    NioDeadlockOnConnectionClosing.class,
    GeneratedClassesTest.class,
    RpcTopologyRecordingTest.class,
    ConnectionTest.class,
    TlsUtilsTest.class,
    ChannelNTest.class,
    RefreshProtectedCredentialsProviderTest.class,
    DefaultCredentialsRefreshServiceTest.class,
    OAuth2ClientCredentialsGrantCredentialsProviderTest.class,
    RefreshCredentialsTest.class,
    AMQConnectionRefreshCredentialsTest.class,
    ValueWriterTest.class
})
public class ClientTests {

    // initialize system properties
    static{
        new AbstractRMQTestSuite(){};
    }

}
