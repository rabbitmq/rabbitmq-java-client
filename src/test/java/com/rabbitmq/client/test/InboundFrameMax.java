// Copyright (c) 2026 Broadcom. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.AMQCommand;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class InboundFrameMax {

  private static final int FRAME_METHOD = 1;
  private static final int FRAME_END = 206;

  @ParameterizedTest
  @ValueSource(ints = {8192, 10_000})
  void oversizedFrameShouldPassWhenEqualToLimit(int frameMax) throws Exception {
    // just the limit
    int openOkPayloadSize = frameMax - AMQCommand.EMPTY_FRAME_SIZE;
    doEnforceInboundFrameMax(frameMax, openOkPayloadSize, false);
  }

  @ParameterizedTest
  @ValueSource(ints = {8192, 10_000})
  void oversizedFrameShouldNotPassWhenAboveLimit(int frameMax) throws Exception {
    // just above the limit
    int openOkPayloadSize = frameMax - AMQCommand.EMPTY_FRAME_SIZE + 1;
    doEnforceInboundFrameMax(frameMax, openOkPayloadSize, true);
  }

  private void doEnforceInboundFrameMax(int frameMax, int openOkPayloadSize, boolean shouldFail)
      throws Exception {

    CountDownLatch serverDone = new CountDownLatch(1);
    AtomicReference<Throwable> serverError = new AtomicReference<>();

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      int port = server.getLocalPort();
      Thread peer =
          new Thread(
              () -> runFakeBroker(server, serverDone, serverError, frameMax, openOkPayloadSize),
              "fake-amqp-broker");
      peer.setDaemon(true);
      peer.start();

      ConnectionFactory factory = TestUtils.connectionFactory();
      factory.setHost("127.0.0.1");
      factory.setPort(port);
      factory.setRequestedFrameMax(frameMax);
      factory.setHandshakeTimeout(5000);
      factory.setConnectionTimeout(5000);
      factory.setRequestedHeartbeat(0);

      if (shouldFail) {
        assertThatThrownBy(() -> factory.newConnection()).isInstanceOf(IOException.class);
      } else {
        try (Connection connection = factory.newConnection()) {
          assertThat(connection.getFrameMax()).isEqualTo(frameMax);
        }
      }
    }
    assertThat(serverDone.await(5, TimeUnit.SECONDS)).isTrue();
    if (!shouldFail) {
      assertThat(serverError.get()).isNull();
    }
  }

  // A negotiated frame_max of 0 means "no limit" (client requests 0, the default, and the
  // broker also declares 0). The configured message body cap must still be enforced in that
  // case, instead of being silently discarded in favor of an effectively unbounded frame size.
  @ParameterizedTest
  @ValueSource(ints = {8192, 10_000})
  void bodySizeCapShouldPassWhenEqualToLimitAndNegotiatedFrameMaxIsUnlimited(
      int maxInboundMessageBodySize) throws Exception {
    int openOkPayloadSize = maxInboundMessageBodySize - AMQCommand.EMPTY_FRAME_SIZE;
    doEnforceInboundFrameMaxWithUnlimitedNegotiatedFrameMax(
        maxInboundMessageBodySize, openOkPayloadSize, false);
  }

  @ParameterizedTest
  @ValueSource(ints = {8192, 10_000})
  void bodySizeCapShouldNotPassWhenAboveLimitAndNegotiatedFrameMaxIsUnlimited(
      int maxInboundMessageBodySize) throws Exception {
    int openOkPayloadSize = maxInboundMessageBodySize - AMQCommand.EMPTY_FRAME_SIZE + 1;
    doEnforceInboundFrameMaxWithUnlimitedNegotiatedFrameMax(
        maxInboundMessageBodySize, openOkPayloadSize, true);
  }

  private void doEnforceInboundFrameMaxWithUnlimitedNegotiatedFrameMax(
      int maxInboundMessageBodySize, int openOkPayloadSize, boolean shouldFail) throws Exception {

    CountDownLatch serverDone = new CountDownLatch(1);
    AtomicReference<Throwable> serverError = new AtomicReference<>();

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      int port = server.getLocalPort();
      // frame_max of 0 in connection.tune means "no limit"
      Thread peer =
          new Thread(
              () -> runFakeBroker(server, serverDone, serverError, 0, openOkPayloadSize),
              "fake-amqp-broker");
      peer.setDaemon(true);
      peer.start();

      ConnectionFactory factory = TestUtils.connectionFactory();
      factory.setHost("127.0.0.1");
      factory.setPort(port);
      // requested frame max of 0 (the client default) is what allows the negotiated
      // frame_max to come out to 0 when the broker also declares 0
      factory.setRequestedFrameMax(0);
      factory.setMaxInboundMessageBodySize(maxInboundMessageBodySize);
      factory.setHandshakeTimeout(5000);
      factory.setConnectionTimeout(5000);
      factory.setRequestedHeartbeat(0);

      if (shouldFail) {
        assertThatThrownBy(() -> factory.newConnection()).isInstanceOf(IOException.class);
      } else {
        try (Connection connection = factory.newConnection()) {
          assertThat(connection.getFrameMax()).isEqualTo(0);
        }
      }
    }
    assertThat(serverDone.await(5, TimeUnit.SECONDS)).isTrue();
    if (!shouldFail) {
      assertThat(serverError.get()).isNull();
    }
  }

  // connection.tune's frame_max is parsed as a signed 32-bit value; a broker (or a
  // misconfigured client requesting a nonzero frame max) can drive the negotiated value
  // negative, which must be rejected rather than silently accepted as a bogus limit.
  @Test
  void negativeNegotiatedFrameMaxShouldBeRejected() throws Exception {
    CountDownLatch serverDone = new CountDownLatch(1);
    AtomicReference<Throwable> serverError = new AtomicReference<>();

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      int port = server.getLocalPort();
      Thread peer =
          new Thread(
              () -> runFakeBrokerWithNegativeFrameMax(server, serverDone, serverError),
              "fake-amqp-broker");
      peer.setDaemon(true);
      peer.start();

      ConnectionFactory factory = TestUtils.connectionFactory();
      factory.setHost("127.0.0.1");
      factory.setPort(port);
      // a nonzero requested frame max is needed for negotiatedMaxValue to take the
      // Math.min(positive, negative) branch, instead of laundering the broker's negative
      // value into 0 ("no limit") the way it would if the client requested 0
      factory.setRequestedFrameMax(131_072);
      factory.setAutomaticRecoveryEnabled(false);
      factory.setHandshakeTimeout(5000);
      factory.setConnectionTimeout(5000);
      factory.setRequestedHeartbeat(0);

      // asserting on the message, not just the type, matters here: on the Netty transport,
      // an unguarded negative frame max reaches Netty's frame decoder and trips its own
      // IllegalArgumentException ("maxFrameLength ... expected: > 0") for an unrelated
      // reason, which would otherwise make this test pass without the fix in place
      assertThatThrownBy(() -> factory.newConnection())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Negotiated frame max cannot be negative");
    }
    assertThat(serverDone.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(serverError.get()).isNull();
  }

  private static void runFakeBrokerWithNegativeFrameMax(
      ServerSocket server, CountDownLatch done, AtomicReference<Throwable> error) {
    try (Socket socket = server.accept()) {
      socket.setSoTimeout(5000);
      DataInputStream in = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      byte[] header = new byte[8];
      in.readFully(header);
      writeMethodFrame(out, startPayload());
      readFrame(in);
      writeMethodFrame(out, tunePayload(-1));
      // the client must reject the negotiated frame max before replying, so no further
      // frames are expected
    } catch (Throwable t) {
      error.set(t);
    } finally {
      done.countDown();
    }
  }

  private static void runFakeBroker(
      ServerSocket server,
      CountDownLatch done,
      AtomicReference<Throwable> error,
      int frameMax,
      int openOkPayloadSize) {
    try (Socket socket = server.accept()) {
      socket.setSoTimeout(5000);
      DataInputStream in = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      byte[] header = new byte[8];
      in.readFully(header);
      writeMethodFrame(out, startPayload());
      readFrame(in);
      writeMethodFrame(out, tunePayload(frameMax));
      readFrame(in);
      readFrame(in);
      writeOversizedOpenOk(out, openOkPayloadSize);
      readFrame(in);
      writeMethodFrame(out, closeOkPayload());
      done.countDown();
    } catch (Throwable t) {
      error.set(t);
      done.countDown();
    }
  }

  private static byte[] startPayload() {
    Buffer b = new Buffer();
    b.u16(10).u16(10);
    b.u8(0).u8(9);
    b.u32(0);
    b.longstr("PLAIN");
    b.longstr("en_US");
    return b.bytes();
  }

  private static byte[] tunePayload(int frameMax) {
    Buffer b = new Buffer();
    b.u16(10).u16(30);
    b.u16(0);
    b.u32(frameMax);
    b.u16(0);
    return b.bytes();
  }

  private static byte[] closeOkPayload() {
    Buffer b = new Buffer();
    b.u16(10).u16(51);
    return b.bytes();
  }

  private static void writeOversizedOpenOk(DataOutputStream out, int size) throws Exception {
    byte[] payload = new byte[size];
    payload[0] = 0;
    payload[1] = 10;
    payload[2] = 0;
    payload[3] = 41;
    payload[4] = 0;
    writeFrame(out, FRAME_METHOD, 0, payload);
  }

  private static void writeMethodFrame(DataOutputStream out, byte[] payload) throws Exception {
    writeFrame(out, FRAME_METHOD, 0, payload);
  }

  private static void writeFrame(DataOutputStream out, int type, int channel, byte[] payload)
      throws Exception {
    out.writeByte(type);
    out.writeShort(channel);
    out.writeInt(payload.length);
    out.write(payload);
    out.writeByte(FRAME_END);
    out.flush();
  }

  private static void readFrame(DataInputStream in) throws Exception {
    in.readUnsignedByte();
    in.readUnsignedShort();
    int size = in.readInt();
    byte[] payload = new byte[size];
    in.readFully(payload);
    int end = in.readUnsignedByte();
    if (end != FRAME_END) {
      throw new AssertionError("bad client frame end: " + end);
    }
  }

  private static final class Buffer {
    private byte[] data = new byte[64];
    private int size;

    Buffer u8(int value) {
      ensure(1);
      data[size++] = (byte) value;
      return this;
    }

    Buffer u16(int value) {
      ensure(2);
      data[size++] = (byte) (value >>> 8);
      data[size++] = (byte) value;
      return this;
    }

    Buffer u32(int value) {
      ensure(4);
      data[size++] = (byte) (value >>> 24);
      data[size++] = (byte) (value >>> 16);
      data[size++] = (byte) (value >>> 8);
      data[size++] = (byte) value;
      return this;
    }

    Buffer longstr(String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      u32(bytes.length);
      ensure(bytes.length);
      System.arraycopy(bytes, 0, data, size, bytes.length);
      size += bytes.length;
      return this;
    }

    byte[] bytes() {
      byte[] copy = new byte[size];
      System.arraycopy(data, 0, copy, 0, size);
      return copy;
    }

    private void ensure(int count) {
      if (size + count <= data.length) {
        return;
      }
      byte[] next = new byte[Math.max(data.length * 2, size + count)];
      System.arraycopy(data, 0, next, 0, size);
      data = next;
    }
  }
}
