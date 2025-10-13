// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;
import org.junit.jupiter.api.BeforeEach;

/**
 * This tests whether bindings are created and nuked properly.
 *
 * The tests attempt to declare durable queues on a secondary node, if
 * present, and that node is restarted as part of the tests while the
 * primary node is still running. That way we exercise any node-down
 * handler code in the server.
 *
 */
public class BindingLifecycleBase extends ClusteredTestBase {
  protected static final int N = 1;
  protected static final byte[] payload = ("" + System.currentTimeMillis()).getBytes();

  protected String q, x, k;

  @BeforeEach
  void initNames() {
    this.q = generateQueueName();
    this.x = generateExchangeName();
    this.k = "K-" + System.currentTimeMillis();
  }

  protected static String randomString() {
    return "-" + System.nanoTime();
  }

  protected void createQueueAndBindToExchange(Binding binding, boolean durable) throws IOException {
    channel.exchangeDeclare(binding.x, "direct", durable);
    channel.queueDeclare(binding.q, durable, false, false, null);
    channel.queueBind(binding.q, binding.x, binding.k);
  }

  protected void declareDurableQueue(String q) throws IOException {
    alternateChannel.queueDeclare(q, true, false, false, null);
  }

  protected void deleteExchangeAndQueue(Binding binding) throws IOException {
    channel.queueDelete(binding.q);
    channel.exchangeDelete(binding.x);
  }

  protected void doAutoDelete(boolean durable, int queues) throws IOException, TimeoutException {
    List<String> queueNames = new ArrayList<>();
    Binding binding = Binding.randomBinding();
    channel.exchangeDeclare(binding.x, "direct", durable, true, null);
    channel.queueDeclare(binding.q, durable, false, true, null);
    channel.queueBind(binding.q, binding.x, binding.k);
    if (queues > 1) {
      int j = queues - 1;
      for (int i = 0; i < j; i++) {
        queueNames.add(randomString());
        channel.queueDeclare(queueNames.get(i), durable, false, false, null);
        channel.queueBind(queueNames.get(i), binding.x, binding.k);
        channel.basicConsume(queueNames.get(i), true, new QueueingConsumer(channel));
      }
    }
    subscribeSendUnsubscribe(binding);
    if (durable) {
      restart();
    }
    if (queues > 1) {
      for (String q : queueNames) {
        channel.basicConsume(q, true, new QueueingConsumer(channel));
        Binding tmp = new Binding(q, binding.x, binding.k);
        sendUnroutable(tmp);
      }
    }
    waitAtMost(() -> {
      Channel ch = connection.createChannel();
      try {
        ch.queueDeclarePassive(binding.q);
      } catch (IOException e) {
        return true;
      }
      return false;
    });
    channel.queueDeclare(binding.q, durable, true, true, null);
    // if (queues == 1): Because the exchange does not exist, this
    // bind should fail
    try {
      channel.queueBind(binding.q, binding.x, binding.k);
      sendRoutable(binding);
    } catch (IOException e) {
      checkShutdownSignal(AMQP.NOT_FOUND, e);
      channel = null;
      return;
    }
    if (queues == 1) {
      deleteExchangeAndQueue(binding);
      fail("Queue bind should have failed");
    }
    // Do some cleanup
    if (queues > 1) {
      for (String q : queueNames) {
        channel.queueDelete(q);
      }
    }
  }

  @Override
  protected void restart() throws IOException, TimeoutException {
  }

  protected void sendRoutable(Binding binding) throws IOException {
    channel.basicPublish(binding.x, binding.k, null, payload);
    GetResponse response = channel.basicGet(binding.q, true);
    assertNotNull(response, "The response should not be null");
  }

  protected void sendUnroutable(Binding binding) throws IOException {
    channel.basicPublish(binding.x, binding.k, null, payload);
    GetResponse response = channel.basicGet(binding.q, true);
    assertNull(response, "The response SHOULD BE null");
  }

  protected Binding setupExchangeAndRouteMessage(boolean durable) throws IOException {
    Binding binding = setupExchangeBindings(durable);
    sendRoutable(binding);
    return binding;
  }

  protected Binding setupExchangeBindings(boolean durable) throws IOException {
    Binding binding = Binding.randomBinding();
    createQueueAndBindToExchange(binding, durable);
    return binding;
  }

  protected void subscribeSendUnsubscribe(Binding binding) throws IOException {
    String tag = channel.basicConsume(binding.q, new QueueingConsumer(channel));
    sendUnroutable(binding);
    channel.basicCancel(tag);
  }

  protected static class Binding {

    final String q;
    final String x;
    final String k;

    static Binding randomBinding() {
      return new Binding(randomString(), randomString(), randomString());
    }

    protected Binding(String q, String x, String k) {
      this.q = q;
      this.x = x;
      this.k = k;
    }
  }


  // A couple of tests that are common to the subclasses (which differ on
  // whether the broker is restarted)

  /**
   *
   * The same thing as testExchangeAutoDelete, but with durable
   * queues.
   *
   * Main difference is restarting the broker to make sure that the
   * durable queues are blasted away.
   */
  public void testExchangeAutoDeleteDurable() throws IOException, TimeoutException {
    doAutoDelete(true, 1);
  }

  /**
   * The same thing as testExchangeAutoDeleteManyBindings, but with
   * durable queues.
   */
  public void testExchangeAutoDeleteDurableManyBindings() throws IOException, TimeoutException {
    doAutoDelete(true, 10);
  }
}
