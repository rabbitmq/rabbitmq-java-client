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
package com.rabbitmq.client.impl;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.SocketConfigurator;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.net.ssl.SSLHandshakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NettyFrameHandlerFactory extends AbstractFrameHandlerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(NettyFrameHandlerFactory.class);
  private final EventLoopGroup eventLoopGroup;
  private final Function<String, SslContext> sslContextFactory;
  private final Consumer<Channel> channelCustomizer;
  private final Consumer<Bootstrap> bootstrapCustomizer;
  private final Duration enqueuingTimeout;
  private final Predicate<ShutdownSignalException> willRecover;

  public NettyFrameHandlerFactory(
      EventLoopGroup eventLoopGroup,
      Consumer<Channel> channelCustomizer,
      Consumer<Bootstrap> bootstrapCustomizer,
      Function<String, SslContext> sslContextFactory,
      Duration enqueuingTimeout,
      int connectionTimeout,
      SocketConfigurator configurator,
      int maxInboundMessageBodySize,
      boolean automaticRecovery,
      Predicate<ShutdownSignalException> recoveryCondition) {
    super(connectionTimeout, configurator, sslContextFactory != null, maxInboundMessageBodySize);
    this.eventLoopGroup = eventLoopGroup;
    this.sslContextFactory = sslContextFactory == null ? connName -> null : sslContextFactory;
    this.channelCustomizer = channelCustomizer == null ? Utils.noOpConsumer() : channelCustomizer;
    this.bootstrapCustomizer =
        bootstrapCustomizer == null ? Utils.noOpConsumer() : bootstrapCustomizer;
    this.enqueuingTimeout = enqueuingTimeout;
    this.willRecover =
        sse -> {
          if (!automaticRecovery) {
            return false;
          } else {
            try {
              return recoveryCondition.test(sse);
            } catch (Exception e) {
              // we assume it will recover, so we take the safe path to dispatch the closing
              // it avoids the risk of deadlock
              return true;
            }
          }
        };
  }

  private static void closeNettyState(Channel channel, EventLoopGroup eventLoopGroup) {
    try {
      if (channel != null && channel.isOpen()) {
        LOGGER.debug("Closing Netty channel");
        channel.close().get(10, TimeUnit.SECONDS);
      } else {
        LOGGER.debug("No Netty channel to close");
      }
    } catch (InterruptedException e) {
      LOGGER.info("Channel closing has been interrupted");
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOGGER.info("Channel closing failed", e);
    } catch (TimeoutException e) {
      LOGGER.info("Could not close channel in 10 seconds");
    }
    try {
      if (eventLoopGroup != null
          && (!eventLoopGroup.isShuttingDown() || !eventLoopGroup.isShutdown())) {
        LOGGER.debug("Closing Netty event loop group");
        eventLoopGroup.shutdownGracefully(1, 10, SECONDS).get(10, SECONDS);
      }
    } catch (InterruptedException e) {
      LOGGER.info("Event loop group closing has been interrupted");
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOGGER.info("Event loop group closing failed", e);
    } catch (TimeoutException e) {
      LOGGER.info("Could not close event loop group in 10 seconds");
    }
  }

  @Override
  public FrameHandler create(Address addr, String connectionName) throws IOException {
    SslContext sslContext = this.sslContextFactory.apply(connectionName);
    return new NettyFrameHandler(
        this.maxInboundMessageBodySize,
        addr,
        sslContext,
        this.eventLoopGroup,
        this.enqueuingTimeout,
        this.willRecover,
        this.channelCustomizer,
        this.bootstrapCustomizer);
  }

  private static final class NettyFrameHandler implements FrameHandler {

    private static final String HANDLER_FLUSH_CONSOLIDATION =
        FlushConsolidationHandler.class.getSimpleName();
    private static final String HANDLER_FRAME_DECODER =
        LengthFieldBasedFrameDecoder.class.getSimpleName();
    private static final String HANDLER_READ_TIMEOUT = ReadTimeoutHandler.class.getSimpleName();
    private static final String HANDLER_IDLE_STATE = IdleStateHandler.class.getSimpleName();
    private static final String HANDLER_PROTOCOL_VERSION_MISMATCH =
        ProtocolVersionMismatchHandler.class.getSimpleName();
    private static final byte[] HEADER =
        new byte[] {
          'A', 'M', 'Q', 'P', 0, AMQP.PROTOCOL.MAJOR, AMQP.PROTOCOL.MINOR, AMQP.PROTOCOL.REVISION
        };
    private final EventLoopGroup eventLoopGroup;
    private final Duration enqueuingTimeout;
    private final Channel channel;
    private final AmqpHandler handler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private NettyFrameHandler(
        int maxInboundMessageBodySize,
        Address addr,
        SslContext sslContext,
        EventLoopGroup elg,
        Duration enqueuingTimeout,
        Predicate<ShutdownSignalException> willRecover,
        Consumer<Channel> channelCustomizer,
        Consumer<Bootstrap> bootstrapCustomizer)
        throws IOException {
      this.enqueuingTimeout = enqueuingTimeout;
      Bootstrap b = new Bootstrap();
      bootstrapCustomizer.accept(b);
      if (b.config().group() == null) {
        if (elg == null) {
          elg = Utils.eventLoopGroup();
          this.eventLoopGroup = elg;
        } else {
          this.eventLoopGroup = null;
        }
        b.group(elg);
      } else {
        this.eventLoopGroup = null;
      }
      if (b.config().channelFactory() == null) {
        b.channel(NioSocketChannel.class);
      }
      if (!b.config().options().containsKey(ChannelOption.SO_KEEPALIVE)) {
        b.option(ChannelOption.SO_KEEPALIVE, true);
      }
      if (!b.config().options().containsKey(ChannelOption.ALLOCATOR)) {
        b.option(ChannelOption.ALLOCATOR, Utils.byteBufAllocator());
      }

      // type + channel + payload size + payload + frame end marker
      int maxFrameLength = 1 + 2 + 4 + maxInboundMessageBodySize + 1;
      int lengthFieldOffset = 3;
      int lengthFieldLength = 4;
      int lengthAdjustement = 1;
      AmqpHandler amqpHandler =
          new AmqpHandler(maxInboundMessageBodySize, this::close, willRecover);
      int port = ConnectionFactory.portOrDefault(addr.getPort(), sslContext != null);
      b.handler(
          new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
              ch.pipeline()
                  .addFirst(
                      HANDLER_FLUSH_CONSOLIDATION,
                      new FlushConsolidationHandler(
                          FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true));
              ch.pipeline()
                  .addLast(HANDLER_PROTOCOL_VERSION_MISMATCH, new ProtocolVersionMismatchHandler());
              ch.pipeline()
                  .addLast(
                      HANDLER_FRAME_DECODER,
                      new LengthFieldBasedFrameDecoder(
                          maxFrameLength,
                          lengthFieldOffset,
                          lengthFieldLength,
                          lengthAdjustement,
                          0));
              ch.pipeline().addLast(AmqpHandler.class.getSimpleName(), amqpHandler);
              if (sslContext != null) {
                SslHandler sslHandler = sslContext.newHandler(ch.alloc(), addr.getHost(), port);
                ch.pipeline().addFirst("ssl", sslHandler);
              }
              if (channelCustomizer != null) {
                channelCustomizer.accept(ch);
              }
            }
          });
      ChannelFuture cf = null;
      try {
        cf = b.connect(addr.getHost(), port).sync();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Error when opening network connection", e);
      } catch (Exception e) {
        if (e.getCause() instanceof IOException) {
          throw (IOException) e.getCause();
        } else {
          throw new IOException("Error when opening network connection", e);
        }
      }
      this.channel = cf.channel();
      this.handler = amqpHandler;
    }

    @Override
    public boolean internalHearbeat() {
      return true;
    }

    @Override
    public int getTimeout() {
      return this.channel.getOption(ChannelOption.SO_TIMEOUT);
    }

    @Override
    public void setTimeout(int timeoutMs) {
      this.maybeRemoveHandler(HANDLER_READ_TIMEOUT);
      timeoutMs = Math.max(1000, timeoutMs * 4);
      this.channel
          .pipeline()
          .addBefore(
              HANDLER_FRAME_DECODER,
              HANDLER_READ_TIMEOUT,
              new ReadTimeoutHandler(timeoutMs, MILLISECONDS));
      if (handler.connection != null) {
        Duration heartbeat = Duration.ofSeconds(handler.connection.getHeartbeat());
        maybeRemoveHandler(HANDLER_IDLE_STATE);
        if (heartbeat.toMillis() > 0) {
          this.channel
              .pipeline()
              .addBefore(
                  HANDLER_FRAME_DECODER,
                  HANDLER_IDLE_STATE,
                  new IdleStateHandler(
                      (int) heartbeat.multipliedBy(2).getSeconds(),
                      (int) heartbeat.getSeconds(),
                      0));
        }
      }
    }

    private void maybeRemoveHandler(String name) {
      if (this.channel.pipeline().get(name) != null) {
        this.channel.pipeline().remove(name);
      }
    }

    @Override
    public void sendHeader() {
      ByteBuf bb = this.channel.alloc().buffer(HEADER.length);
      bb.writeBytes(HEADER);
      this.channel.writeAndFlush(bb);
    }

    @Override
    public void initialize(AMQConnection connection) {
      this.handler.connection = connection;
    }

    @Override
    public void finishConnectionNegotiation() {
      maybeRemoveHandler(HANDLER_PROTOCOL_VERSION_MISMATCH);
    }

    @Override
    public Frame readFrame() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void writeFrame(Frame frame) throws IOException {
      if (this.handler.isWritable()) {
        this.doWriteFrame(frame);
      } else {
        if (this.channel.eventLoop().inEventLoop()) {
          // we do not wait in the event loop
          this.doWriteFrame(frame);
        } else {
          // we get the current latch
          CountDownLatch latch = this.handler.writableLatch();
          if (this.handler.isWritable()) {
            // the channel became  writable
            this.doWriteFrame(frame);
          } else {
            try {
              // the channel is still non-writable
              // in case its writability flipped, we have a reference to a latch that has been
              // counted down
              // so, worst case scenario, we'll enqueue only one frame right away
              boolean canWriteNow = latch.await(enqueuingTimeout.toMillis(), MILLISECONDS);
              if (canWriteNow) {
                this.doWriteFrame(frame);
              } else {
                throw new IOException("Frame enqueuing failed");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
    }

    private void doWriteFrame(Frame frame) throws IOException {
      ByteBuf bb = this.channel.alloc().buffer(frame.size());
      frame.writeToByteBuf(bb);
      this.channel.writeAndFlush(bb);
    }

    @Override
    public void flush() {
      this.channel.flush();
    }

    @Override
    public void close() {
      if (this.closed.compareAndSet(false, true)) {
        Runnable closing = () -> closeNettyState(this.channel, this.eventLoopGroup);
        if (this.channel.eventLoop().inEventLoop()) {
          this.channel.eventLoop().submit(closing);
        } else {
          closing.run();
        }
      }
    }

    @Override
    public InetAddress getLocalAddress() {
      InetSocketAddress addr = maybeInetSocketAddress(this.channel.localAddress());
      return addr == null ? null : addr.getAddress();
    }

    @Override
    public int getLocalPort() {
      InetSocketAddress addr = maybeInetSocketAddress(this.channel.localAddress());
      return addr == null ? -1 : addr.getPort();
    }

    @Override
    public InetAddress getAddress() {
      InetSocketAddress addr = maybeInetSocketAddress(this.channel.remoteAddress());
      return addr == null ? null : addr.getAddress();
    }

    @Override
    public int getPort() {
      InetSocketAddress addr = maybeInetSocketAddress(this.channel.remoteAddress());
      return addr == null ? -1 : addr.getPort();
    }

    InetSocketAddress maybeInetSocketAddress(SocketAddress socketAddress) {
      if (socketAddress instanceof InetSocketAddress) {
        return (InetSocketAddress) socketAddress;
      } else {
        return null;
      }
    }
  }

  private static class AmqpHandler extends ChannelInboundHandlerAdapter {

    private final int maxPayloadSize;
    private final Runnable closeSequence;
    private final Predicate<ShutdownSignalException> willRecover;
    private volatile AMQConnection connection;
    private volatile Channel ch;
    private final AtomicBoolean writable = new AtomicBoolean(true);
    private final AtomicReference<CountDownLatch> writableLatch =
        new AtomicReference<>(new CountDownLatch(1));

    private AmqpHandler(
        int maxPayloadSize,
        Runnable closeSequence,
        Predicate<ShutdownSignalException> willRecover) {
      this.maxPayloadSize = maxPayloadSize;
      this.closeSequence = closeSequence;
      this.willRecover = willRecover;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      this.ch = ctx.channel();
      super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      ByteBuf m = (ByteBuf) msg;
      try {
        int type = m.readUnsignedByte();
        int channel = m.readUnsignedShort();
        int payloadSize = m.readInt();
        if (payloadSize >= maxPayloadSize) {
          throw new IllegalStateException(
              format(
                  "Frame body is too large (%d), maximum configured size is %d. "
                      + "See ConnectionFactory#setMaxInboundMessageBodySize "
                      + "if you need to increase the limit.",
                  payloadSize, maxPayloadSize));
        }

        byte[] payload = new byte[payloadSize];
        m.readBytes(payload);

        int frameEndMarker = m.readUnsignedByte();
        if (frameEndMarker != AMQP.FRAME_END) {
          throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
        }

        Frame frame = new Frame(type, channel, payload);
        this.connection.ioLoopThread(Thread.currentThread());
        boolean noProblem = this.connection.handleReadFrame(frame);
        if (noProblem
            && (!this.connection.isRunning() || this.connection.hasBrokerInitiatedShutdown())) {
          // looks like the frame was Close-Ok or Close
          this.dispatchShutdownToConnection(() -> this.connection.doFinalShutdown());
        }
      } finally {
        m.release();
      }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
      boolean canWrite = ctx.channel().isWritable();
      if (this.writable.compareAndSet(!canWrite, canWrite)) {
        if (canWrite) {
          CountDownLatch latch = writableLatch.getAndSet(new CountDownLatch(1));
          latch.countDown();
        } else {
          ctx.channel().flush();
        }
      }
      super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      if (needToDispatchIoError()) {
        AMQConnection c = this.connection;
        if (c.isOpen()) {
          // it is likely to be an IO exception
          this.dispatchShutdownToConnection(() -> c.handleIoError(null));
        } else {
          // just in case, the call is idempotent anyway
          this.dispatchShutdownToConnection(c::doFinalShutdown);
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (cause instanceof DecoderException && cause.getCause() instanceof SSLHandshakeException) {
        LOGGER.debug("Error during TLS handshake");
        this.handleIoError(cause.getCause());
      } else {
        this.handleIoError(cause);
      }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      if (evt instanceof IdleStateEvent) {
        IdleStateEvent e = (IdleStateEvent) evt;
        if (e.state() == IdleState.READER_IDLE) {
          LOGGER.info(
              "Closing connection {} on {}:{} because it's been idle for too long",
              this.connection.getClientProvidedName(),
              this.connection.getAddress().getHostName(),
              this.connection.getPort());
          if (needToDispatchIoError()) {
            this.dispatchShutdownToConnection(() -> this.connection.handleHeartbeatFailure());
          }
        } else if (e.state() == IdleState.WRITER_IDLE) {
          this.connection.writeFrame(new Frame(AMQP.FRAME_HEARTBEAT, 0));
          this.connection.flush();
        }
      }
      super.userEventTriggered(ctx, evt);
    }

    private void handleIoError(Throwable cause) {
      if (needToDispatchIoError()) {
        this.dispatchShutdownToConnection(() -> this.connection.handleIoError(cause));
      } else {
        this.closeSequence.run();
      }
    }

    private boolean needToDispatchIoError() {
      AMQConnection c = this.connection;
      return c != null && c.isOpen();
    }

    private boolean isWritable() {
      return this.writable.get();
    }

    private CountDownLatch writableLatch() {
      return this.writableLatch.get();
    }

    protected void dispatchShutdownToConnection(Runnable connectionShutdownRunnable) {
      String name = "rabbitmq-connection-shutdown";
      AMQConnection c = this.connection;
      if (c == null || ch == null) {
        // not enough information, we dispatch in separate thread
        Environment.newThread(connectionShutdownRunnable, name).start();
      } else {
        if (ch.eventLoop().inEventLoop()) {
          if (this.willRecover.test(c.getCloseReason())) {
            // the connection will recover, we don't want this to happen in the event loop,
            // it could cause a deadlock, so using a separate thread
            name = name + "-" + c;
            System.out.println("in separate thread");
            Environment.newThread(connectionShutdownRunnable, name).start();
          } else {
            // no recovery, it is safe to dispatch in the event loop
            System.out.println("in event loop");
            ch.eventLoop().submit(connectionShutdownRunnable);
          }
        } else {
          // not in the event loop, we can run it in the same thread
          connectionShutdownRunnable.run();
        }
      }
    }
  }

  private static final class ProtocolVersionMismatchHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      ByteBuf b = (ByteBuf) msg;
      if (b.readByte() == 'A') {
        // likely an AMQP header that indicates a protocol version mismatch
        // the header is small, we read everything in memory and use the Frame class
        int toRead = Math.min(b.readableBytes(), NettyFrameHandler.HEADER.length - 1);
        byte[] header = new byte[toRead];
        b.readBytes(header);
        Frame.protocolVersionMismatch(new DataInputStream(new ByteArrayInputStream(header)));
      } else {
        b.readerIndex(0);
        ctx.fireChannelRead(msg);
      }
    }
  }
}
