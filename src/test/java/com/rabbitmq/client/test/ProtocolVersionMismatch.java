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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.AddressResolver;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ListAddressResolver;
import com.rabbitmq.client.MalformedFrameException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolVersionMismatch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolVersionMismatch.class);

  @ParameterizedTest
  @MethodSource("com.rabbitmq.client.test.TestUtils#ioLayers")
  void connectionShouldFailWithProtocolVersionMismatch(String ioLayer) throws Exception {
    int port = TestUtils.randomNetworkPort();
    try (ProtocolVersionMismatchServer ignored = new ProtocolVersionMismatchServer(port)) {
      ConnectionFactory cf = TestUtils.connectionFactory();
      TestUtils.setIoLayer(cf, ioLayer);
      cf.setPort(port);
      AddressResolver addressResolver =
          new ListAddressResolver(Collections.singletonList(new Address("localhost", port)));
      assertThatThrownBy(() -> cf.newConnection(addressResolver))
          .hasRootCauseInstanceOf(MalformedFrameException.class);
    }
  }

  private static class ProtocolVersionMismatchServer implements AutoCloseable {

    private final EventLoopGroup elp =
        new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

    private static final byte[] AMQP_HEADER = new byte[] {'A', 'M', 'Q', 'P', 0, 1, 0, 0};

    private ProtocolVersionMismatchServer(int port) throws InterruptedException {
      ServerBootstrap b = new ServerBootstrap();
      b.group(elp);
      b.channel(NioServerSocketChannel.class);
      b.childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                  ch.pipeline()
                      .addLast(
                          new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                              LOGGER.debug("Client connection in test AMQP 1.0 server");
                              // discard the data
                              ReferenceCountUtil.release(msg);
                              ByteBuf b = ctx.alloc().buffer(AMQP_HEADER.length);
                              b.writeBytes(AMQP_HEADER);
                              ctx.channel()
                                  .writeAndFlush(b)
                                  .addListener(ChannelFutureListener.CLOSE);
                            }
                          });
                }
              })
          .validate();
      b.bind(port).sync();
    }

    @Override
    public void close() {
      this.elp.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    }
  }
}
