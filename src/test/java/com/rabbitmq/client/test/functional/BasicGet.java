// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.test.BrokerTestCase;

public class BasicGet extends BrokerTestCase {
  @Test public void basicGetWithEnqueuedMessages() throws IOException, InterruptedException {
    assertTrue(channel.isOpen());
    String q = channel.queueDeclare().getQueue();

    basicPublishPersistent("msg".getBytes("UTF-8"), q);
    Thread.sleep(250);

    assertNotNull(channel.basicGet(q, true));
    channel.queuePurge(q);
    assertNull(channel.basicGet(q, true));
    channel.queueDelete(q);
  }

  @Test public void basicGetWithEmptyQueue() throws IOException, InterruptedException {
    assertTrue(channel.isOpen());
    String q = channel.queueDeclare().getQueue();

    assertNull(channel.basicGet(q, true));
    channel.queueDelete(q);
  }

  @Test public void basicGetWithClosedChannel() throws IOException, InterruptedException, TimeoutException {
    assertTrue(channel.isOpen());
    String q = channel.queueDeclare().getQueue();

    channel.close();
    assertFalse(channel.isOpen());
    try {
      channel.basicGet(q, true);
      fail("expected basic.get on a closed channel to fail");
    } catch (AlreadyClosedException e) {
      // passed
    } finally {
      Channel tch = connection.createChannel();
      tch.queueDelete(q);
      tch.close();
    }

  }
}
