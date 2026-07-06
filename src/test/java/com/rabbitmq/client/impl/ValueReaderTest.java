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
package com.rabbitmq.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.MalformedFrameException;
import java.io.*;
import org.junit.jupiter.api.Test;

public class ValueReaderTest {

  @Test
  public void longstrWrittenAndReadMatches() throws IOException {
    String value = "a long string value";
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ValueWriter valueWriter = new ValueWriter(new DataOutputStream(outputStream));
    valueWriter.writeLongstr(value);

    ValueReader valueReader =
        new ValueReader(new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray())));
    assertThat(valueReader.readLongstr().toString()).isEqualTo(value);
  }

  @Test
  public void readLongstrWithLengthMatchingAvailableDataShouldSucceed() throws IOException {
    byte[] content = "hello".getBytes();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    new DataOutputStream(outputStream).writeInt(content.length);
    outputStream.write(content);

    ValueReader valueReader =
        new ValueReader(new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray())));
    assertThat(valueReader.readLongstr().toString()).isEqualTo("hello");
  }

  @Test
  public void readLongstrWithClaimedLengthExceedingAvailableDataShouldFail() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    // claim a huge length, but do not provide the corresponding bytes
    new DataOutputStream(outputStream).writeInt(Integer.MAX_VALUE - 1);

    ValueReader valueReader =
        new ValueReader(new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray())));
    assertThatThrownBy(valueReader::readLongstr).isInstanceOf(MalformedFrameException.class);
  }

  @Test
  public void readLongstrWithClaimedLengthSlightlyExceedingAvailableDataShouldFail()
      throws IOException {
    byte[] content = "hello".getBytes();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    // claim one more byte than what is actually available
    new DataOutputStream(outputStream).writeInt(content.length + 1);
    outputStream.write(content);

    ValueReader valueReader =
        new ValueReader(new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray())));
    assertThatThrownBy(valueReader::readLongstr).isInstanceOf(MalformedFrameException.class);
  }

  @Test
  public void readFieldValueByteArrayWithClaimedLengthExceedingAvailableDataShouldFail()
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
    dataOutputStream.writeByte('x');
    // claim a huge length, but do not provide the corresponding bytes
    dataOutputStream.writeInt(Integer.MAX_VALUE - 1);

    DataInputStream in = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
    assertThatThrownBy(() -> ValueReader.readFieldValue(in))
        .isInstanceOf(MalformedFrameException.class);
  }
}
