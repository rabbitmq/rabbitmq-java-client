// Copyright (c) 2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.SocketFrameHandler;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

/**
 * Verifies that once the negotiated frame_max is applied to the inbound reader, a frame whose total
 * size exceeds frame_max is rejected, while a frame of exactly frame_max is still accepted.
 */
public class NegotiatedFrameMaxInboundTest {

  private static final int FRAME_MAX = 4096;

  // Same limit AMQConnection applies once frame_max is negotiated.
  private static final int INBOUND_PAYLOAD_LIMIT = FRAME_MAX - AMQCommand.EMPTY_FRAME_SIZE + 1;

  @Test
  void frameOfExactlyFrameMaxIsAccepted() throws Exception {
    int payloadSize = FRAME_MAX - AMQCommand.EMPTY_FRAME_SIZE;
    Frame frame = readSingleFrame(payloadSize);
    assertThat(frame.getPayload()).hasSize(payloadSize);
    assertThat(frame.size()).isEqualTo(FRAME_MAX);
  }

  @Test
  void frameLargerThanFrameMaxIsRejected() {
    int payloadSize = FRAME_MAX - AMQCommand.EMPTY_FRAME_SIZE + 1; // total frame size = frame_max + 1
    assertThatThrownBy(() -> readSingleFrame(payloadSize))
        .isInstanceOf(MalformedFrameException.class);
  }

  private static Frame readSingleFrame(int payloadSize) throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (ServerSocket server = new ServerSocket(0, 1, loopback);
        Socket client = new Socket(loopback, server.getLocalPort());
        Socket peer = server.accept()) {
      DataOutputStream out = new DataOutputStream(peer.getOutputStream());
      out.writeByte(AMQP.FRAME_METHOD);
      out.writeShort(0);
      out.writeInt(payloadSize);
      out.write(new byte[payloadSize]);
      out.writeByte(AMQP.FRAME_END);
      out.flush();

      SocketFrameHandler handler = new SocketFrameHandler(client);
      handler.setFrameMax(INBOUND_PAYLOAD_LIMIT);
      return handler.readFrame();
    }
  }
}
