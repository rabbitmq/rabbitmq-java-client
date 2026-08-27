// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class ShortstrRoundTripTest {

  private static String read(byte[] payload) throws IOException {
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(payload.length);
    frame.write(payload);
    return new ValueReader(new DataInputStream(new ByteArrayInputStream(frame.toByteArray())))
        .readShortstr();
  }

  private static byte[] write(String s) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new ValueWriter(new DataOutputStream(out)).writeShortstr(s);
    return out.toByteArray();
  }

  @Test
  public void valueReadFromWireCanBeWrittenBack() throws IOException {
    byte[] payload = new byte[255];
    Arrays.fill(payload, (byte) 0xFF);
    String decoded = read(payload);
    assertThat(decoded.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
    assertThatCode(() -> write(decoded)).doesNotThrowAnyException();
  }

  @Test
  public void wellFormedValuesArePreserved() throws IOException {
    for (String value : new String[] {"", "hello", "santé", "你好", "😀"}) {
      assertThat(read(value.getBytes(StandardCharsets.UTF_8))).isEqualTo(value);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 85; i++) {
      sb.append("你");
    }
    String maxLength = sb.toString();
    assertThat(maxLength.getBytes(StandardCharsets.UTF_8).length).isEqualTo(255);
    assertThat(read(maxLength.getBytes(StandardCharsets.UTF_8))).isEqualTo(maxLength);
  }

  @Test
  public void partiallyMalformedValueStaysWithinLimit() throws IOException {
    byte[] payload = new byte[255];
    Arrays.fill(payload, (byte) 'a');
    for (int i = 100; i < 255; i++) {
      payload[i] = (byte) 0xFF;
    }
    String decoded = read(payload);
    assertThat(decoded.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
    assertThatCode(() -> write(decoded)).doesNotThrowAnyException();
    assertThat(decoded).startsWith("aaaa");
  }

  @Test
  public void messagePropertiesReadFromWireCanBeWrittenBack() throws IOException {
    byte[] malformed = new byte[255];
    Arrays.fill(malformed, (byte) 0xFF);

    ByteArrayOutputStream header = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(header);
    out.writeShort(0);
    out.writeLong(6);
    out.writeShort((1 << 10) | (1 << 9) | (1 << 7) | (1 << 5) | (1 << 4));
    out.writeByte(malformed.length);
    out.write(malformed);
    out.writeByte(malformed.length);
    out.write(malformed);
    out.writeByte(malformed.length);
    out.write(malformed);
    out.writeByte(malformed.length);
    out.write(malformed);
    out.writeByte(malformed.length);
    out.write(malformed);
    out.flush();

    AMQP.BasicProperties properties =
        new AMQP.BasicProperties(
            new DataInputStream(new ByteArrayInputStream(header.toByteArray())));

    assertThat(properties.getCorrelationId()).isNotNull();
    assertThat(properties.getReplyTo()).isNotNull();
    assertThat(properties.getMessageId()).isNotNull();
    assertThat(properties.getType()).isNotNull();
    assertThat(properties.getUserId()).isNotNull();

    AMQP.BasicProperties echoed =
        new AMQP.BasicProperties.Builder()
            .correlationId(properties.getCorrelationId())
            .replyTo(properties.getReplyTo())
            .messageId(properties.getMessageId())
            .type(properties.getType())
            .userId(properties.getUserId())
            .build();

    assertThatCode(
            () -> {
              ByteArrayOutputStream sink = new ByteArrayOutputStream();
              echoed.writePropertiesTo(
                  new com.rabbitmq.client.impl.ContentHeaderPropertyWriter(
                      new DataOutputStream(sink)));
            })
        .doesNotThrowAnyException();
  }

  @Test
  public void oversizedValuesAreStillRejected() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 256; i++) {
      sb.append('a');
    }
    assertThatCode(() -> write(sb.toString())).isInstanceOf(IllegalArgumentException.class);
  }
}
