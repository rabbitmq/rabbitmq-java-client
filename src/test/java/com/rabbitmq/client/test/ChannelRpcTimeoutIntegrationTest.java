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

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class ChannelRpcTimeoutIntegrationTest {

    static long waitTimeOnSomeResponses = 1000L;

    ConnectionFactory factory;

    @Before
    public void setUp() {
        factory = TestUtils.connectionFactory();
    }

    @After
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
            connection.close();
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
                assertThat((Channel) e.getChannel(), is(channel));
                assertThat(e.getChannelNumber(), is(channel.getChannelNumber()));
                assertThat(e.getMethod(), instanceOf(AMQP.Queue.Declare.class));
            }
        } finally {
            connection.close();
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
            if(command.getMethod() instanceof AMQImpl.Queue.DeclareOk) {
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
