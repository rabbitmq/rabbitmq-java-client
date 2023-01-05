// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Test for bug 20004 - deadlock through internal synchronization on the channel object. This is
 * more properly a unit test, but since it requires a connection to a broker, it's grouped with the
 * functional tests.
 *
 * <p>Test calls channel.queueDeclare, while synchronising on channel, from an independent thread.
 */
public class Bug20004Test extends BrokerTestCase {
  private volatile Exception caughtException = null;
  private volatile boolean created = false;

  protected void releaseResources() throws IOException {
    if (created) {
      channel.queueDelete("Bug20004Test");
    }
  }

  @Test
  public void bug20004() throws InterruptedException {
    final Bug20004Test testInstance = this;
    CountDownLatch completedLatch = new CountDownLatch(1);

    Thread declaringThread =
        new Thread(
            () -> {
              try {
                synchronized (channel) {
                  channel.queueDeclare("Bug20004Test", false, false, false, null);
                  testInstance.created = true;
                }
              } catch (Exception e) {
                testInstance.caughtException = e;
              }
              completedLatch.countDown();
            });
    declaringThread.start();

    boolean completed = completedLatch.await(5, TimeUnit.SECONDS);

    assertTrue(completed, "Deadlock detected?");
    assertNull(caughtException, "queueDeclare threw an exception");
    assertTrue(created, "unknown sequence of events");
  }
}
