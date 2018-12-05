// Copyright (c) 2017-Present Pivotal Software, Inc.  All rights reserved.
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
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.impl.recovery.RecordedBinding;
import com.rabbitmq.client.impl.recovery.RecordedConsumer;
import com.rabbitmq.client.impl.recovery.RecordedExchange;
import com.rabbitmq.client.impl.recovery.RecordedQueue;
import com.rabbitmq.client.impl.recovery.TopologyRecoveryFilter;
import com.rabbitmq.tools.Host;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RpcTest {

    Connection clientConnection, serverConnection;
    Channel clientChannel, serverChannel;
    String queue = "rpc.queue";
    RpcServer rpcServer;

    @Before
    public void init() throws Exception {
        clientConnection = TestUtils.connectionFactory().newConnection();
        clientChannel = clientConnection.createChannel();
        serverConnection = TestUtils.connectionFactory().newConnection();
        serverChannel = serverConnection.createChannel();
        serverChannel.queueDeclare(queue, false, false, false, null);
    }

    @After
    public void tearDown() throws Exception {
        if (rpcServer != null) {
            rpcServer.terminateMainloop();
        }
        if (serverChannel != null) {
            serverChannel.queueDelete(queue);
        }
        TestUtils.close(clientConnection);
        TestUtils.close(serverConnection);
    }

    @Test
    public void rpc() throws Exception {
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(queue).timeout(1000));
        RpcClient.Response response = client.doCall(null, "hello".getBytes());
        assertEquals("*** hello ***", new String(response.getBody()));
        assertEquals("pre-hello", response.getProperties().getHeaders().get("pre").toString());
        assertEquals("post-hello", response.getProperties().getHeaders().get("post").toString());
        client.close();
    }

    @Test
    public void rpcUnroutableShouldTimeout() throws Exception {
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();
        String noWhereRoutingKey = UUID.randomUUID().toString();
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(noWhereRoutingKey)
                .timeout(500));
        try {
            client.primitiveCall("".getBytes());
            fail("Unroutable message, call should have timed out");
        } catch (TimeoutException e) {
            // OK
        }
        client.close();
    }

    @Test
    public void rpcUnroutableWithMandatoryFlagShouldThrowUnroutableException() throws Exception {
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();
        String noWhereRoutingKey = UUID.randomUUID().toString();
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(noWhereRoutingKey)
                .timeout(1000).useMandatory());
        String content = UUID.randomUUID().toString();
        try {
            client.primitiveCall(content.getBytes());
            fail("Unroutable message with mandatory enabled, an exception should have been thrown");
        } catch (UnroutableRpcRequestException e) {
            assertEquals(noWhereRoutingKey, e.getReturnMessage().getRoutingKey());
            assertEquals(content, new String(e.getReturnMessage().getBody()));
        }
        client.close();
    }

    @Test
    public void rpcCustomReplyHandler() throws Exception {
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();
        AtomicInteger replyHandlerCalls = new AtomicInteger(0);
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(queue).timeout(1000)
                .replyHandler(reply -> {
                    replyHandlerCalls.incrementAndGet();
                    return RpcClient.DEFAULT_REPLY_HANDLER.apply(reply);
                })
        );
        assertEquals(0, replyHandlerCalls.get());
        RpcClient.Response response = client.doCall(null, "hello".getBytes());
        assertEquals(1, replyHandlerCalls.get());
        assertEquals("*** hello ***", new String(response.getBody()));
        assertEquals("pre-hello", response.getProperties().getHeaders().get("pre").toString());
        assertEquals("post-hello", response.getProperties().getHeaders().get("post").toString());
        client.doCall(null, "hello".getBytes());
        assertEquals(2, replyHandlerCalls.get());
        client.close();
    }

    @Test
    public void rpcResponseTimeout() throws Exception {
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(queue));
        try {
            client.doCall(null, "hello".getBytes(), 200);
        } catch (TimeoutException e) {
            // OK
        }
        assertEquals(0, client.getContinuationMap().size());
        client.close();
    }

    @Test
    public void givenConsumerNotRecoveredCanCreateNewClientOnSameChannelAfterConnectionFailure() throws Exception {
        // see https://github.com/rabbitmq/rabbitmq-java-client/issues/382
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();

        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setTopologyRecoveryFilter(new NoDirectReplyToConsumerTopologyRecoveryFilter());
        cf.setNetworkRecoveryInterval(1000);
        Connection connection = null;
        try {
            connection = cf.newConnection();
            Channel channel = connection.createChannel();
            RpcClient client = new RpcClient(new RpcClientParams()
                    .channel(channel).exchange("").routingKey(queue).timeout(1000));
            RpcClient.Response response = client.doCall(null, "hello".getBytes());
            assertEquals("*** hello ***", new String(response.getBody()));
            final CountDownLatch recoveryLatch = new CountDownLatch(1);
            ((AutorecoveringConnection) connection).addRecoveryListener(new RecoveryListener() {

                @Override
                public void handleRecovery(Recoverable recoverable) {
                    recoveryLatch.countDown();
                }

                @Override
                public void handleRecoveryStarted(Recoverable recoverable) {

                }
            });
            Host.closeConnection((NetworkConnection) connection);
            assertTrue("Connection should have recovered by now", recoveryLatch.await(10, TimeUnit.SECONDS));
            client = new RpcClient(new RpcClientParams()
                    .channel(channel).exchange("").routingKey(queue).timeout(1000));
            response = client.doCall(null, "hello".getBytes());
            assertEquals("*** hello ***", new String(response.getBody()));
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void givenConsumerIsRecoveredCanNotCreateNewClientOnSameChannelAfterConnectionFailure() throws Exception {
        // see https://github.com/rabbitmq/rabbitmq-java-client/issues/382
        rpcServer = new TestRpcServer(serverChannel, queue);
        new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();

        ConnectionFactory cf = TestUtils.connectionFactory();
        cf.setNetworkRecoveryInterval(1000);
        Connection connection = null;
        try {
            connection = cf.newConnection();
            Channel channel = connection.createChannel();
            RpcClient client = new RpcClient(new RpcClientParams()
                    .channel(channel).exchange("").routingKey(queue).timeout(1000));
            RpcClient.Response response = client.doCall(null, "hello".getBytes());
            assertEquals("*** hello ***", new String(response.getBody()));
            final CountDownLatch recoveryLatch = new CountDownLatch(1);
            ((AutorecoveringConnection) connection).addRecoveryListener(new RecoveryListener() {

                @Override
                public void handleRecovery(Recoverable recoverable) {
                    recoveryLatch.countDown();
                }

                @Override
                public void handleRecoveryStarted(Recoverable recoverable) {

                }
            });
            Host.closeConnection((NetworkConnection) connection);
            assertTrue("Connection should have recovered by now", recoveryLatch.await(10, TimeUnit.SECONDS));
            try {
                new RpcClient(new RpcClientParams()
                        .channel(channel).exchange("").routingKey(queue).timeout(1000));
                fail("Cannot create RPC client on same channel, an exception should have been thrown");
            } catch (IOException e) {
                assertTrue(e.getCause() instanceof ShutdownSignalException);
                ShutdownSignalException cause = (ShutdownSignalException) e.getCause();
                assertTrue(cause.getReason() instanceof AMQP.Channel.Close);
                assertEquals(406, ((AMQP.Channel.Close) cause.getReason()).getReplyCode());
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test public void interruptingServerThreadShouldStopIt() throws Exception {
        rpcServer = new TestRpcServer(serverChannel, queue);
        Thread serverThread = new Thread(() -> {
            try {
                rpcServer.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        });
        serverThread.start();
        RpcClient client = new RpcClient(new RpcClientParams()
                .channel(clientChannel).exchange("").routingKey(queue).timeout(1000));
        RpcClient.Response response = client.doCall(null, "hello".getBytes());
        assertEquals("*** hello ***", new String(response.getBody()));

        serverThread.interrupt();

        waitAtMost(Duration.ONE_SECOND).until(() -> !serverThread.isAlive()) ;

        client.close();
    }

    private static class TestRpcServer extends RpcServer {

        public TestRpcServer(Channel channel, String queueName) throws IOException {
            super(channel, queueName);
        }

        @Override
        protected AMQP.BasicProperties preprocessReplyProperties(Delivery request, AMQP.BasicProperties.Builder builder) {
            Map<String, Object> headers = new HashMap<>();
            headers.put("pre", "pre-" + new String(request.getBody()));
            builder.headers(headers);
            return builder.build();
        }

        @Override
        public byte[] handleCall(Delivery request, AMQP.BasicProperties replyProperties) {
            String input = new String(request.getBody());
            return ("*** " + input + " ***").getBytes();
        }

        @Override
        protected AMQP.BasicProperties postprocessReplyProperties(Delivery request, AMQP.BasicProperties.Builder builder) {
            Map<String, Object> headers = new HashMap<String, Object>(builder.build().getHeaders());
            headers.put("post", "post-" + new String(request.getBody()));
            builder.headers(headers);
            return builder.build();
        }
    }

    private static class NoDirectReplyToConsumerTopologyRecoveryFilter implements TopologyRecoveryFilter {

        @Override
        public boolean filterExchange(RecordedExchange recordedExchange) {
            return true;
        }

        @Override
        public boolean filterQueue(RecordedQueue recordedQueue) {
            return true;
        }

        @Override
        public boolean filterBinding(RecordedBinding recordedBinding) {
            return true;
        }

        @Override
        public boolean filterConsumer(RecordedConsumer recordedConsumer) {
            return !"amq.rabbitmq.reply-to".equals(recordedConsumer.getQueue());
        }
    }
}
