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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQCommand;
import com.rabbitmq.client.impl.AMQContentHeader;
import com.rabbitmq.client.impl.Frame;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class ByteBufferPublishTest {

  @Test
  void frameFromByteBufferHasCorrectSize() {
    byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
    ByteBuffer buf = ByteBuffer.wrap(data);
    Frame frame = Frame.fromBodyFragment(1, buf, 0, data.length);
    assertThat(frame.size()).isEqualTo(data.length + 8);
  }

  @Test
  void frameFromByteBufferSlice() {
    byte[] data = "AAAA hello BBBB".getBytes(StandardCharsets.UTF_8);
    ByteBuffer buf = ByteBuffer.wrap(data);
    Frame frame = Frame.fromBodyFragment(1, buf, 5, 5);
    byte[] payload = frame.getPayload();
    assertThat(new String(payload, StandardCharsets.UTF_8)).isEqualTo("hello");
  }

  @Test
  void frameFromDirectByteBuffer() {
    byte[] data = "direct buffer test".getBytes(StandardCharsets.UTF_8);
    ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
    direct.put(data);
    direct.flip();
    Frame frame = Frame.fromBodyFragment(1, direct, 0, data.length);
    assertThat(frame.getPayload()).isEqualTo(data);
  }

  @Test
  void frameToString() {
    byte[] data = new byte[42];
    ByteBuffer buf = ByteBuffer.wrap(data);
    Frame frame = Frame.fromBodyFragment(1, buf, 0, data.length);
    assertThat(frame.toString()).contains("42").contains("ByteBuffer");
  }

  @Test
  void amqCommandByteBufferConstructor() {
    byte[] data = "amqcommand test".getBytes(StandardCharsets.UTF_8);
    ByteBuffer buf = ByteBuffer.wrap(data);

    AMQP.Basic.Publish publish =
        new AMQP.Basic.Publish.Builder().exchange("").routingKey("test").build();
    AMQContentHeader header = new AMQP.BasicProperties.Builder().build();
    AMQCommand cmd = new AMQCommand(publish, header, buf);

    assertThat(cmd.getMethod()).isNotNull();
    assertThat(cmd.getContentHeader()).isNotNull();
    assertThat(cmd.getContentBody()).isEqualTo(data);
  }

  @Test
  void frameFromByteBufferWithOffset() {
    byte[] data = new byte[100];
    new Random().nextBytes(data);
    ByteBuffer buf = ByteBuffer.wrap(data);

    Frame frame = Frame.fromBodyFragment(1, buf, 20, 50);
    byte[] payload = frame.getPayload();
    assertThat(payload.length).isEqualTo(50);

    byte[] expected = new byte[50];
    System.arraycopy(data, 20, expected, 0, 50);
    assertThat(payload).isEqualTo(expected);
  }
}
