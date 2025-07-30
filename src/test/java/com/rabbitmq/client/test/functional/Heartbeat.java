// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
// Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.test.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class Heartbeat extends BrokerTestCase {

  @Override
  protected ConnectionFactory newConnectionFactory() {
    ConnectionFactory cf = super.newConnectionFactory();
    cf.setRequestedHeartbeat(1);
    return cf;
  }

  @Test
  public void heartbeat() throws InterruptedException {
    assertEquals(1, connection.getHeartbeat());
    Thread.sleep(3100);
    assertTrue(connection.isOpen());
    ((AutorecoveringConnection) connection).getDelegate().setHeartbeat(0);
    assertEquals(0, connection.getHeartbeat());
    Thread.sleep(3100);
    assertFalse(connection.isOpen());
  }

  @Test
  void nettyHeartbeat() throws Exception {
    ChannelTrafficShapingHandler channelTrafficShapingHandler =
        new ChannelTrafficShapingHandler(0, 0);
    ConnectionFactory cf = TestUtils.connectionFactory();
    cf.netty().channelCustomizer(ch -> ch.pipeline().addFirst(channelTrafficShapingHandler));
    cf.setRequestedHeartbeat(2);
    try (Connection c = cf.newConnection()) {
      TrafficCounter trafficCounter = channelTrafficShapingHandler.trafficCounter();
      long writtenAfterOpening = trafficCounter.cumulativeWrittenBytes();
      long readAfterOpening = trafficCounter.currentReadBytes();

      int heartbeatFrameSize = 4 + 2 + 2;
      int heartbeatFrameCount = 3;
      int expectedAdditionalBytes = heartbeatFrameCount * heartbeatFrameSize;

      TestUtils.waitAtMost(
          Duration.ofSeconds(15),
          () ->
              trafficCounter.cumulativeWrittenBytes()
                      >= writtenAfterOpening + expectedAdditionalBytes
                  && trafficCounter.cumulativeReadBytes()
                      >= readAfterOpening + expectedAdditionalBytes);
      assertThat(c.isOpen()).isTrue();
    }
  }
}
