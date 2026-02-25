// Copyright (c) 2017-2026 Broadcom. All Rights Reserved.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.WriteListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PublishWithByteBufferTest extends BrokerTestCase {

  String queue;

  @Override
  protected void createResources() throws IOException, TimeoutException {
    queue = channel.queueDeclare(UUID.randomUUID().toString(), true, false, false, null).getQueue();
  }

  @Override
  protected void releaseResources() throws IOException {
    channel.queueDelete(queue);
  }

  @ParameterizedTest
  @CsvSource({
    "-1,12",
    "-1,1000",
    "-1,50000",
    "-1,66000",
    "-1,550000",
    "10000,12",
    "10000,1000",
    "10000,50000",
    "10000,550000"
  })
  public void publishByteBuffer(int frameMax, int size) throws Exception {
    byte[] masterData = new byte[size];
    ThreadLocalRandom.current().nextBytes(masterData);
    ConnectionFactory cf = TestUtils.connectionFactory();
    if (frameMax > 0) {
      cf.setRequestedFrameMax(frameMax);
    }
    try (Connection connection = cf.newConnection()) {
      final CountDownLatch consumingLatch = new CountDownLatch(1);
      AtomicReference<byte[]> dataRef = new AtomicReference<>();
      String ctag =
          this.channel.basicConsume(
              queue,
              true,
              (consumerTag, delivery) -> {
                dataRef.set(delivery.getBody());
                consumingLatch.countDown();
              },
              consumerTag -> {});
      Channel publishingChannel = connection.createChannel();
      ByteBuffer buf = ByteBuffer.allocateDirect(size);
      buf.put(masterData);
      buf.flip();
      AtomicBoolean canBeReleased = new AtomicBoolean(false);
      publishingChannel.basicPublish(
          "",
          queue,
          false,
          false,
          null,
          buf,
          (WriteListener) (success, cause) -> canBeReleased.set(success));
      assertTrue(
          consumingLatch.await(1, TimeUnit.SECONDS), "deliver callback should have been called");
      assertThat(dataRef.get()).isEqualTo(masterData);
      assertThat(canBeReleased.get()).isTrue();
      this.channel.basicCancel(ctag);
    }
  }
}
