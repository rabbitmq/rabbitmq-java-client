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
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.impl.NettyFrameHandlerFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueSocketChannel;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void interruptedFrameWriteWaitsForWritability() throws Exception {
    Frame frame =
        new Frame(AMQP.FRAME_BODY, 1, ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    frame.writeTo(new DataOutputStream(expected));

    AtomicReference<io.netty.channel.Channel> nettyChannel = new AtomicReference<>();
    NettyFrameHandlerFactory factory = frameHandlerFactory(nettyChannel);
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
      FrameHandler frameHandler =
          factory.create(
              new Address(loopback.getHostAddress(), server.getLocalPort()), "interrupt-test");
      try (Socket socket = server.accept()) {
        io.netty.channel.Channel ch = nettyChannel.get();
        setWritable(ch, false);

        CountDownLatch writeDone = new CountDownLatch(1);
        AtomicReference<Throwable> writeError = new AtomicReference<>();
        AtomicBoolean interruptedAfterWrite = new AtomicBoolean(false);
        Thread writer =
            new Thread(
                () -> {
                  try {
                    frameHandler.writeFrame(frame);
                  } catch (Throwable e) {
                    writeError.set(e);
                  }
                  interruptedAfterWrite.set(Thread.currentThread().isInterrupted());
                  writeDone.countDown();
                });
        writer.start();
        TestUtils.waitAtMost(() -> writer.getState() == Thread.State.TIMED_WAITING);
        writer.interrupt();
        assertThat(writeDone.await(500, TimeUnit.MILLISECONDS)).isFalse();

        setWritable(ch, true);
        assertThat(writeDone).is(completed());
        assertThat(writeError.get()).isNull();
        assertThat(interruptedAfterWrite).isTrue();

        frameHandler.flush(null);
        socket.setSoTimeout(10_000);
        byte[] received = new byte[expected.size()];
        new DataInputStream(socket.getInputStream()).readFully(received);
        assertThat(received).isEqualTo(expected.toByteArray());
      } finally {
        frameHandler.close();
      }
    }
  }

  @Test
  void frameWriteWaitingForWritabilityFailsWhenChannelIsClosed() throws Exception {
    Frame frame =
        new Frame(AMQP.FRAME_BODY, 1, ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
    AtomicReference<io.netty.channel.Channel> nettyChannel = new AtomicReference<>();
    NettyFrameHandlerFactory factory = frameHandlerFactory(nettyChannel);
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
      FrameHandler frameHandler =
          factory.create(
              new Address(loopback.getHostAddress(), server.getLocalPort()), "close-test");
      try (Socket ignored = server.accept()) {
        io.netty.channel.Channel ch = nettyChannel.get();
        setWritable(ch, false);

        CountDownLatch writeDone = new CountDownLatch(1);
        AtomicReference<Throwable> writeError = new AtomicReference<>();
        Thread writer =
            new Thread(
                () -> {
                  try {
                    frameHandler.writeFrame(frame);
                  } catch (Throwable e) {
                    writeError.set(e);
                  }
                  writeDone.countDown();
                });
        writer.start();
        TestUtils.waitAtMost(() -> writer.getState() == Thread.State.TIMED_WAITING);

        ch.close().get(10, TimeUnit.SECONDS);
        // the enqueuing timeout is 30 seconds, the writer must be released well before
        assertThat(writeDone).is(completed());
        assertThat(writeError.get()).isInstanceOf(IOException.class);
      } finally {
        frameHandler.close();
      }
    }
  }

  private static NettyFrameHandlerFactory frameHandlerFactory(
      AtomicReference<io.netty.channel.Channel> nettyChannel) {
    return new NettyFrameHandlerFactory(
        null,
        nettyChannel::set,
        null,
        null,
        Duration.ofSeconds(30),
        10_000,
        null,
        AMQP.FRAME_MIN_SIZE,
        false,
        sse -> false);
  }

  private static void setWritable(io.netty.channel.Channel ch, boolean writable) throws Exception {
    ch.eventLoop()
        .submit(() -> ch.unsafe().outboundBuffer().setUserDefinedWritability(1, writable))
        .get(10, TimeUnit.SECONDS);
    // Netty fires the writability event in a later event loop task
    ch.eventLoop().submit(() -> {}).get(10, TimeUnit.SECONDS);
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
