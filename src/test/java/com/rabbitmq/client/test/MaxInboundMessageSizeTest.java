// Copyright (c) 2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MaxInboundMessageSizeTest extends BrokerTestCase {

  @Parameterized.Parameter(value = 0)
  public int maxMessageSize;
  @Parameterized.Parameter(value = 1)
  public int frameMax;
  @Parameterized.Parameter(value = 2)
  public boolean basicGet;

  @Parameterized.Parameters
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
            {20000, 5000, true},
            {20000, 100000, true},
            {20000, 5000, false},
            {20000, 100000, false}
        });
  }

  String q;

  private static void safeClose(Connection c) {
    try {
      c.close();
    } catch (Exception e) {
      // OK
    }
  }

  @Override
  protected void createResources() throws IOException, TimeoutException {
    q = generateQueueName();
    declareTransientQueue(q);
    super.createResources();
  }

  @Test
  public void maxInboundMessageSizeMustBeEnforced()
      throws Exception {
    ConnectionFactory cf = newConnectionFactory();
    cf.setMaxInboundMessageBodySize(maxMessageSize);
    cf.setRequestedFrameMax(frameMax);
    Connection c = cf.newConnection();
    try {
      Channel ch = c.createChannel();
      ch.confirmSelect();
      byte[] body = new byte[maxMessageSize * 2];
      ch.basicPublish("", q, null, body);
      ch.waitForConfirmsOrDie();
      AtomicReference<Throwable> exception = new AtomicReference<>();
      CountDownLatch errorLatch = new CountDownLatch(1);
      ch.addShutdownListener(
          cause -> {
            exception.set(cause.getCause());
            errorLatch.countDown();
          });
      if (basicGet) {
        try {
          ch.basicGet(q, true);
        } catch (Exception e) {
          // OK for basicGet
        }
      } else {
        ch.basicConsume(q, new DefaultConsumer(ch));
      }
      assertThat(errorLatch).is(completed());
      assertThat(exception.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Message body is too large");
    } finally {
      safeClose(c);
    }
  }

  @Override
  protected void releaseResources() throws IOException {
    deleteQueue(q);
    super.releaseResources();
  }
}