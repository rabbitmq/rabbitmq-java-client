// Copyright (c) 2025 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import static com.rabbitmq.client.test.TestUtils.LatchConditions.completed;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueSocketChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class NettyTest {

  ConnectionFactory cf;

  @BeforeEach
  void init() {
    cf = TestUtils.connectionFactory();
    TestUtils.setIoLayer(cf, TestUtils.IO_NETTY);
  }

  @Test
  void publishConsumeDefaults() throws Exception {
    publishConsume(cf);
  }

  @Test
  @EnabledOnOs(value = OS.MAC, architectures = "aarch64")
  void kqueue() throws Exception {
    nativeIoTest(KQueueIoHandler.newFactory(), KQueueSocketChannel.class);
  }

  @Test
  @EnabledOnOs(value = OS.LINUX, architectures = "amd64")
  void epoll() throws Exception {
    nativeIoTest(EpollIoHandler.newFactory(), EpollSocketChannel.class);
  }

  private static void nativeIoTest(
      IoHandlerFactory ioHandlerFactory, Class<? extends io.netty.channel.Channel> channelClass)
      throws IOException, TimeoutException {
    ConnectionFactory cf = TestUtils.connectionFactory();
    EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(ioHandlerFactory);
    Set<io.netty.channel.Channel> channels = ConcurrentHashMap.newKeySet();
    cf.netty()
        .eventLoopGroup(eventLoopGroup)
        .bootstrapCustomizer(b -> b.channel(channelClass))
        .channelCustomizer(channels::add);
    try {
      publishConsume(cf);
      assertThat(channels)
          .isNotEmpty()
          .allMatch(ch -> ch.getClass().isAssignableFrom(channelClass));
    } finally {
      eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    }
  }

  private static void publishConsume(ConnectionFactory cf) throws IOException, TimeoutException {
    try (Connection c = cf.newConnection()) {
      Channel ch1 = c.createChannel();
      String q = ch1.queueDeclare().getQueue();

      Channel ch2 = c.createChannel();
      CountDownLatch consumeLatch = new CountDownLatch(1);
      CountDownLatch cancelLatch = new CountDownLatch(1);
      String ctag =
          ch2.basicConsume(
              q,
              new DefaultConsumer(ch2) {
                @Override
                public void handleDelivery(
                    String ctg, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {
                  ch2.basicAck(envelope.getDeliveryTag(), false);
                  consumeLatch.countDown();
                }

                @Override
                public void handleCancelOk(String consumerTag) {
                  cancelLatch.countDown();
                }
              });

      Channel ch3 = c.createChannel();
      ch3.confirmSelect();
      CountDownLatch confirmLatch = new CountDownLatch(1);
      ch3.addConfirmListener(
          (deliveryTag, multiple) -> confirmLatch.countDown(), (dtag, multiple) -> {});
      ch3.basicPublish("", q, null, "hello".getBytes(StandardCharsets.UTF_8));
      assertThat(confirmLatch).is(completed());
      assertThat(consumeLatch).is(completed());

      TestUtils.waitAtMost(() -> ch1.queueDeclarePassive(q).getMessageCount() == 0);

      ch2.basicCancel(ctag);
      assertThat(cancelLatch).is(completed());

      ch1.queueDelete(q);

      ch3.close();
      ch2.close();
      ch1.close();
    }
  }
}
