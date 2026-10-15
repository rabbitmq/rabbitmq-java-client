// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class ChannelRpcTimeoutIntegrationTest {

    static long waitTimeOnSomeResponses = 1000L;

    ConnectionFactory factory;

    @BeforeEach
    public void setUp() {
        factory = TestUtils.connectionFactory();
    }

    @AfterEach
    public void tearDown() {
        factory = null;
    }

    @Test public void channelWaitsWhenNoTimeoutSet() throws IOException, TimeoutException {
        FrameHandler frameHandler = createFrameHandler();
        ConnectionParams params = factory.params(Executors.newFixedThreadPool(1));
        WaitingAmqConnection connection = new WaitingAmqConnection(params, frameHandler);
        try {
            connection.start();
            Channel channel = connection.createChannel();
            channel.queueDeclare();
        } finally {
            connection.close(10_000);
        }

    }

    @Test public void channelThrowsExceptionWhenTimeoutIsSet() throws IOException, TimeoutException {
        FrameHandler frameHandler = createFrameHandler();
        ConnectionParams params = factory.params(Executors.newFixedThreadPool(1));
        params.setChannelRpcTimeout((int) (waitTimeOnSomeResponses / 5.0));
        WaitingAmqConnection connection = new WaitingAmqConnection(params, frameHandler);
        try {
            connection.start();
            Channel channel = connection.createChannel();
            try {
                channel.queueDeclare();
                fail("Should time out and throw an exception");
            } catch(ChannelContinuationTimeoutException e) {
                // OK
                assertThat((Channel) e.getChannel()).isEqualTo(channel);
                assertThat(e.getChannelNumber()).isEqualTo(channel.getChannelNumber());
                assertThat(e.getMethod()).isInstanceOf(AMQP.Queue.Declare.class);
            }
        } finally {
            connection.close(10_000);
        }
    }

    @Test public void channelIsClosedAfterRpcTimeoutAndLateReplyIsDiscarded() throws Exception {
        String queueA = "rpc-timeout-a-" + UUID.randomUUID();
        String queueB = "rpc-timeout-b-" + UUID.randomUUID();
        byte[] body = "message for queue A".getBytes(StandardCharsets.UTF_8);
        FrameHandler frameHandler = createFrameHandler();
        ConnectionParams params = factory.params(Executors.newFixedThreadPool(1));
        params.setChannelRpcTimeout((int) (waitTimeOnSomeResponses / 5.0));
        WaitingAmqConnection connection = new WaitingAmqConnection(params, frameHandler);
        connection.start();
        try (Connection regularConnection = factory.newConnection()) {
            Channel regularChannel = regularConnection.createChannel();
            regularChannel.queueDeclare(queueA, true, false, false, null);
            regularChannel.queueDeclare(queueB, true, false, false, null);
            regularChannel.basicPublish("", queueA, null, body);
            TestUtils.waitAtMost(() -> regularChannel.messageCount(queueA) == 1);
            try {
                Channel channel = connection.createChannel();
                CountDownLatch shutdownLatch = new CountDownLatch(1);
                channel.addShutdownListener(cause -> shutdownLatch.countDown());

                assertThatThrownBy(() -> channel.basicGet(queueA, false))
                    .isInstanceOf(ChannelContinuationTimeoutException.class);
                assertThat(channel.isOpen()).isFalse();
                ShutdownSignalException closeReason = channel.getCloseReason();
                assertThat(closeReason.isHardError()).isFalse();
                assertThat(closeReason.isInitiatedByApplication()).isFalse();
                assertThat(closeReason.getCause()).isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> channel.basicGet(queueB, true))
                    .isInstanceOf(AlreadyClosedException.class);

                // the late reply is discarded and the close-ok eventually arrives
                assertThat(shutdownLatch.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(connection.isOpen()).isTrue();

                // the unacknowledged message is requeued when the channel is closed
                TestUtils.waitAtMost(() -> regularChannel.messageCount(queueA) == 1);
                GetResponse response = regularChannel.basicGet(queueA, true);
                assertThat(response.getBody()).isEqualTo(body);
                assertThat(regularChannel.basicGet(queueB, true)).isNull();
            } finally {
                regularChannel.queueDelete(queueA);
                regularChannel.queueDelete(queueB);
            }
        } finally {
            connection.close(10_000);
        }
    }

    private FrameHandler createFrameHandler() throws IOException {
        SocketFrameHandlerFactory socketFrameHandlerFactory = new SocketFrameHandlerFactory(ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT,
            SocketFactory.getDefault(), SocketConfigurators.defaultConfigurator(), false, null);
        return socketFrameHandlerFactory.create(new Address("localhost"), null);
    }

    static class WaitingChannel extends ChannelN {

        public WaitingChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
            super(connection, channelNumber, workService);
        }

        @Override
        public void handleCompleteInboundCommand(AMQCommand command) throws IOException {
            if(command.getMethod() instanceof AMQImpl.Queue.DeclareOk
                || command.getMethod() instanceof AMQImpl.Basic.GetOk) {
                try {
                    Thread.sleep(waitTimeOnSomeResponses);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            super.handleCompleteInboundCommand(command);
        }
    }

    static class WaitingChannelManager extends ChannelManager {

        public WaitingChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory) {
            super(workService, channelMax, threadFactory);
        }

        @Override
        protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
            return new WaitingChannel(connection, channelNumber, workService);
        }
    }

    static class WaitingAmqConnection extends AMQConnection {

        public WaitingAmqConnection(ConnectionParams params, FrameHandler frameHandler) {
            super(params, frameHandler);
        }

        @Override
        protected ChannelManager instantiateChannelManager(int channelMax, ThreadFactory threadFactory) {
            WaitingChannelManager channelManager = new WaitingChannelManager(_workService, channelMax, threadFactory);
            configureChannelManager(channelManager);
            return channelManager;
        }
    }

}
