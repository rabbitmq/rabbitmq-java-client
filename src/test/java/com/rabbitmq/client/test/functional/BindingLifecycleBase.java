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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.QueueingConsumer;

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
  protected static final String K = "K-" + System.currentTimeMillis();
  protected static final int N = 1;
  protected static final String Q = "Q-" + System.currentTimeMillis();
  protected static final String X = "X-" + System.currentTimeMillis();
  protected static final byte[] payload = ("" + System.currentTimeMillis()).getBytes();

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
    String[] queueNames = null;
    Binding binding = Binding.randomBinding();
    channel.exchangeDeclare(binding.x, "direct", durable, true, null);
    channel.queueDeclare(binding.q, durable, false, true, null);
    channel.queueBind(binding.q, binding.x, binding.k);
    if (queues > 1) {
      int j = queues - 1;
      queueNames = new String[j];
      for (int i = 0; i < j; i++) {
        queueNames[i] = randomString();
        channel.queueDeclare(queueNames[i], durable, false, false, null);
        channel.queueBind(queueNames[i], binding.x, binding.k);
        channel.basicConsume(queueNames[i], true, new QueueingConsumer(channel));
      }
    }
    subscribeSendUnsubscribe(binding);
    if (durable) {
      restart();
    }
    if (queues > 1 && queueNames != null) {
      for (String s : queueNames) {
        channel.basicConsume(s, true, new QueueingConsumer(channel));
        Binding tmp = new Binding(s, binding.x, binding.k);
        sendUnroutable(tmp);
      }
    }
    channel.queueDeclare(binding.q, durable, true, true, null);
    // if (queues == 1): Because the exchange does not exist, this
    // bind should fail
    try {
      channel.queueBind(binding.q, binding.x, binding.k);
      sendRoutable(binding);
    }
    catch (IOException e) {
      checkShutdownSignal(AMQP.NOT_FOUND, e);
      channel = null;
      return;
    }
    if (queues == 1) {
      deleteExchangeAndQueue(binding);
      fail("Queue bind should have failed");
    }
    // Do some cleanup
    if (queues > 1 && queueNames != null) {
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
    assertNotNull("The response should not be null", response);
  }

  protected void sendUnroutable(Binding binding) throws IOException {
    channel.basicPublish(binding.x, binding.k, null, payload);
    GetResponse response = channel.basicGet(binding.q, true);
    assertNull("The response SHOULD BE null", response);
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
