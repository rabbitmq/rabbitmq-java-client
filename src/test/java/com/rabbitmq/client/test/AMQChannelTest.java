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

import com.rabbitmq.client.ChannelContinuationTimeoutException;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.AMQChannel;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AMQChannelTest {

    ScheduledExecutorService scheduler;

    @Before public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After public void tearDown() {
        scheduler.shutdownNow();
    }

    @Test public void rpcTimesOutWhenResponseDoesNotCome() throws IOException {
        int rpcTimeout = 100;
        AMQConnection connection = mock(AMQConnection.class);
        when(connection.getChannelRpcTimeout()).thenReturn(rpcTimeout);
        when(connection.getTrafficListener()).thenReturn(TrafficListener.NO_OP);

        DummyAmqChannel channel = new DummyAmqChannel(connection, 1);
        Method method = new AMQImpl.Queue.Declare.Builder()
            .queue("")
            .durable(false)
            .exclusive(true)
            .autoDelete(true)
            .arguments(null)
            .build();

        try {
            channel.rpc(method);
            fail("Should time out and throw an exception");
        } catch(ChannelContinuationTimeoutException e) {
            // OK
            assertThat((DummyAmqChannel) e.getChannel(), is(channel));
            assertThat(e.getChannelNumber(), is(channel.getChannelNumber()));
            assertThat(e.getMethod(), is(method));
            assertNull("outstanding RPC should have been cleaned", channel.nextOutstandingRpc());
        }
    }

    @Test public void rpcReturnsResultWhenResponseHasCome() throws IOException {
        int rpcTimeout = 1000;
        AMQConnection connection = mock(AMQConnection.class);
        when(connection.getChannelRpcTimeout()).thenReturn(rpcTimeout);
        when(connection.getTrafficListener()).thenReturn(TrafficListener.NO_OP);

        final DummyAmqChannel channel = new DummyAmqChannel(connection, 1);
        Method method = new AMQImpl.Queue.Declare.Builder()
            .queue("")
            .durable(false)
            .exclusive(true)
            .autoDelete(true)
            .arguments(null)
            .build();

        final Method response = new AMQImpl.Queue.DeclareOk.Builder()
            .queue("whatever")
            .consumerCount(0)
            .messageCount(0).build();

        scheduler.schedule(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                channel.handleCompleteInboundCommand(new AMQCommand(response));
                return null;
            }
        }, (long) (rpcTimeout / 2.0), TimeUnit.MILLISECONDS);

        AMQCommand rpcResponse = channel.rpc(method);
        assertThat(rpcResponse.getMethod(), is(response));
    }

    @Test
    public void testRpcTimeoutReplyComesDuringNexRpc() throws Exception {
        int rpcTimeout = 100;
        AMQConnection connection = mock(AMQConnection.class);
        when(connection.getChannelRpcTimeout()).thenReturn(rpcTimeout);
        when(connection.willCheckRpcResponseType()).thenReturn(Boolean.TRUE);
        when(connection.getTrafficListener()).thenReturn(TrafficListener.NO_OP);

        final DummyAmqChannel channel = new DummyAmqChannel(connection, 1);
        Method method = new AMQImpl.Queue.Declare.Builder()
            .queue("123")
            .durable(false)
            .exclusive(true)
            .autoDelete(true)
            .arguments(null)
            .build();

        try {
            channel.rpc(method);
            fail("Should time out and throw an exception");
        } catch(final ChannelContinuationTimeoutException e) {
            // OK
            assertThat((DummyAmqChannel) e.getChannel(), is(channel));
            assertThat(e.getChannelNumber(), is(channel.getChannelNumber()));
            assertThat(e.getMethod(), is(method));
            assertNull("outstanding RPC should have been cleaned", channel.nextOutstandingRpc());
        }

        // now do a basic.consume request and have the queue.declareok returned instead
        method = new AMQImpl.Basic.Consume.Builder()
            .queue("123")
            .consumerTag("")
            .arguments(null)
            .build();

        final Method response1 = new AMQImpl.Queue.DeclareOk.Builder()
            .queue("123")
            .consumerCount(0)
            .messageCount(0).build();

        final Method response2 = new AMQImpl.Basic.ConsumeOk.Builder()
            .consumerTag("456").build();

        scheduler.schedule(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                channel.handleCompleteInboundCommand(new AMQCommand(response1));
                Thread.sleep(10);
                channel.handleCompleteInboundCommand(new AMQCommand(response2));
                return null;
            }
        }, (long) (rpcTimeout / 2.0), TimeUnit.MILLISECONDS);

        AMQCommand rpcResponse = channel.rpc(method);
        assertThat(rpcResponse.getMethod(), is(response2));
    }

    static class DummyAmqChannel extends AMQChannel {

        public DummyAmqChannel(AMQConnection connection, int channelNumber) {
            super(connection, channelNumber);
        }

        @Override
        public boolean processAsync(Command command) throws IOException {
            return false;
        }
    }

}
