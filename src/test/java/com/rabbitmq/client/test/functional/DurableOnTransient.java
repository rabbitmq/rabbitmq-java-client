// Copyright (c) 2007-2026 Broadcom. All Rights Reserved.
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
package com.rabbitmq.client.test.functional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class DurableOnTransient extends ClusteredTestBase {
  private String q;
  private String x;

  private GetResponse basicGet() throws IOException {
    return channel.basicGet(q, true);
  }

  private void basicPublish() throws IOException {
    channel.basicPublish(
        x, "", MessageProperties.PERSISTENT_TEXT_PLAIN, "persistent message".getBytes());
  }

  protected void createResources() throws IOException {
    x = generateExchangeName();
    q = generateQueueName();
    channel.exchangeDelete(x);
    // transient exchange
    channel.exchangeDeclare(x, "direct", false);

    channel.queueDelete(q);
    // durable queue
    channel.queueDeclare(q, true, false, false, null);
  }

  protected void releaseResources() throws IOException {
    channel.queueDelete(q);
    channel.exchangeDelete(x);
  }

  @Test
  public void bindDurableToTransient() throws IOException {
    channel.queueBind(q, x, "");
    basicPublish();
    assertNotNull(basicGet());
  }

  @Test
  public void semiDurableBindingRemoval() throws IOException {
    if (clusteredConnection != null) {
      String exchange = generateExchangeName();
      String queue = generateQueueName();
      declareTransientTopicExchange(exchange);
      clusteredChannel.queueDeclare(queue, true, false, false, null);
      channel.queueBind(queue, exchange, "k");

      stopSecondary();
      boolean restarted = false;
      try {
        deleteExchange(exchange);

        startSecondary();
        restarted = true;

        declareTransientTopicExchange(exchange);

        basicPublishVolatile(exchange, "k");
        assertDelivered(queue, 0);

        deleteQueue(queue);
        deleteExchange(exchange);
      } finally {
        if (!restarted) {
          startSecondary();
        }
      }
    }
  }
}
