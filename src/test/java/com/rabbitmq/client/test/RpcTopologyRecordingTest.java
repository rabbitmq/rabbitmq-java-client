// Copyright (c) 2018-Present Pivotal Software, Inc.  All rights reserved.
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
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.rabbitmq.client.test.TestUtils.closeAndWaitForRecovery;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RpcTopologyRecordingTest extends BrokerTestCase {

    @Parameterized.Parameter
    public RpcCall rpcCall;
    String exchange, queue, routingKey;
    String exchange2, queue2, routingKey2;

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[]{
                (RpcCall) (channel, method) -> channel.asyncCompletableRpc(method).get(5, TimeUnit.SECONDS),
                (RpcCall) (channel, method) -> channel.rpc(method)
        };
    }

    @Override
    protected ConnectionFactory newConnectionFactory() {
        ConnectionFactory connectionFactory = super.newConnectionFactory();
        connectionFactory.setNetworkRecoveryInterval(2);
        return connectionFactory;
    }

    @Override
    protected void createResources() throws IOException, TimeoutException {
        super.createResources();
        queue = UUID.randomUUID().toString();
        exchange = UUID.randomUUID().toString();
        routingKey = UUID.randomUUID().toString();
        queue2 = "e2e-" + UUID.randomUUID().toString();
        exchange2 = "e2e-" + UUID.randomUUID().toString();
        routingKey2 = "e2e-" + UUID.randomUUID().toString();
    }

    @Override
    protected void releaseResources() throws IOException {
        super.releaseResources();
        channel.exchangeDelete(exchange);
        channel.exchangeDelete(exchange2);
    }

    @Test
    public void topologyRecovery() throws Exception {
        createTopology();

        AtomicReference<CountDownLatch> latch = new AtomicReference<>(new CountDownLatch(2));
        DeliverCallback countDown = (ctag, message) -> latch.get().countDown();
        channel.basicConsume(queue, countDown, consumerTag -> {
        });
        channel.basicConsume(queue2, countDown, consumerTag -> {
        });

        channel.basicPublish(exchange, routingKey, null, "".getBytes());
        channel.basicPublish(exchange, routingKey2, null, "".getBytes());

        assertTrue(latch.get().await(5, TimeUnit.SECONDS));

        latch.set(new CountDownLatch(2));

        closeAndWaitForRecovery((RecoverableConnection) connection);

        channel.basicPublish(exchange, routingKey, null, "".getBytes());
        channel.basicPublish(exchange, routingKey2, null, "".getBytes());
        assertTrue(latch.get().await(5, TimeUnit.SECONDS));
    }

    @Test
    public void deletionAreProperlyRecorded() throws Exception {
        createTopology();

        AtomicReference<CountDownLatch> latch = new AtomicReference<>(new CountDownLatch(2));
        DeliverCallback countDown = (ctag, message) -> latch.get().countDown();
        String ctag1 = channel.basicConsume(queue, countDown, consumerTag -> {
        });
        String ctag2 = channel.basicConsume(queue2, countDown, consumerTag -> {
        });

        channel.basicPublish(exchange, routingKey, null, "".getBytes());
        channel.basicPublish(exchange, routingKey2, null, "".getBytes());

        assertTrue(latch.get().await(5, TimeUnit.SECONDS));

        channel.basicCancel(ctag1);
        channel.basicCancel(ctag2);

        rpcCall.call(channel, new AMQImpl.Exchange.Delete.Builder().exchange(exchange).build());
        rpcCall.call(channel, new AMQImpl.Exchange.Delete.Builder().exchange(exchange2).build());
        rpcCall.call(channel, new AMQImpl.Queue.Delete.Builder().queue(queue).build());
        rpcCall.call(channel, new AMQImpl.Queue.Delete.Builder().queue(queue2).build());


        latch.set(new CountDownLatch(2));

        closeAndWaitForRecovery((RecoverableConnection) connection);

        assertFalse(queueExists(queue));
        assertFalse(queueExists(queue2));
        assertFalse(exchangeExists(exchange));
        assertFalse(exchangeExists(exchange2));
    }

    boolean queueExists(String queue) throws TimeoutException {
        try (Channel ch = connection.createChannel()) {
            ch.queueDeclarePassive(queue);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    boolean exchangeExists(String exchange) throws TimeoutException {
        try (Channel ch = connection.createChannel()) {
            ch.exchangeDeclarePassive(exchange);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    public void bindingDeletionAreProperlyRecorded() throws Exception {
        createTopology();

        AtomicReference<CountDownLatch> latch = new AtomicReference<>(new CountDownLatch(2));
        DeliverCallback countDown = (ctag, message) -> latch.get().countDown();
        channel.basicConsume(queue, countDown, consumerTag -> {
        });
        channel.basicConsume(queue2, countDown, consumerTag -> {
        });

        channel.basicPublish(exchange, routingKey, null, "".getBytes());
        channel.basicPublish(exchange, routingKey2, null, "".getBytes());

        assertTrue(latch.get().await(5, TimeUnit.SECONDS));

        unbind();

        latch.set(new CountDownLatch(2));

        closeAndWaitForRecovery((RecoverableConnection) connection);

        channel.basicPublish(exchange, routingKey, null, "".getBytes());
        channel.basicPublish(exchange, routingKey2, null, "".getBytes());
        assertFalse(latch.get().await(2, TimeUnit.SECONDS));
    }

    private void createTopology() throws Exception {
        createAndBind(exchange, queue, routingKey);
        createAndBind(exchange2, queue2, routingKey2);
        rpcCall.call(channel, new AMQImpl.Exchange.Bind.Builder()
                .source(exchange)
                .destination(exchange2)
                .routingKey(routingKey2)
                .arguments(null)
                .build());
    }

    private void createAndBind(String e, String q, String rk) throws Exception {
        rpcCall.call(channel, new AMQImpl.Queue.Declare.Builder()
                .queue(q)
                .durable(false)
                .exclusive(true)
                .autoDelete(false)
                .arguments(null)
                .build());
        rpcCall.call(channel, new AMQImpl.Exchange.Declare.Builder()
                .exchange(e)
                .type("direct")
                .durable(false)
                .autoDelete(false)
                .arguments(null)
                .build());
        rpcCall.call(channel, new AMQImpl.Queue.Bind.Builder()
                .queue(q)
                .exchange(e)
                .routingKey(rk)
                .arguments(null)
                .build());
    }

    private void unbind() throws Exception {
        rpcCall.call(channel, new AMQImpl.Queue.Unbind.Builder()
                .exchange(exchange)
                .queue(queue)
                .routingKey(routingKey).build()
        );

        rpcCall.call(channel, new AMQImpl.Queue.Unbind.Builder()
                .exchange(exchange2)
                .queue(queue2)
                .routingKey(routingKey2).build()
        );

        rpcCall.call(channel, new AMQImpl.Exchange.Unbind.Builder()
                .source(exchange)
                .destination(exchange2)
                .routingKey(routingKey2).build()
        );
    }

    @FunctionalInterface
    interface RpcCall {

        void call(Channel channel, Method method) throws Exception;

    }

}
