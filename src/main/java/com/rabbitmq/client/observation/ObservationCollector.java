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
package com.rabbitmq.client.observation;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;

/**
 * API to instrument operations in the AMQP client. The supported operations are publishing,
 * asynchronous delivery, and synchronous delivery (<code>basic.get</code>).
 *
 * <p>Implementations can gather information and send it to tracing backends. This allows e.g.
 * following the processing steps of a given message through different systems.
 *
 * <p>This is considered an SPI and is susceptible to change at any time.
 *
 * @since 5.18.0
 * @see com.rabbitmq.client.ConnectionFactory#setObservationCollector( ObservationCollector)
 */
public interface ObservationCollector {

  ObservationCollector NO_OP = new NoOpObservationCollector();

  /**
   * Decorate message publishing.
   *
   * <p>Implementations are expected to call {@link PublishCall#publish( PublishCall,
   * AMQP.Basic.Publish, AMQP.BasicProperties, byte[], ConnectionInfo)} to make sure the message is
   * actually sent.
   *
   * @param call
   * @param publish
   * @param properties
   * @param body
   * @param connectionInfo
   * @throws IOException
   */
  void publish(
      PublishCall call,
      AMQP.Basic.Publish publish,
      AMQP.BasicProperties properties,
      byte[] body,
      ConnectionInfo connectionInfo)
      throws IOException;

  /**
   * Decorate consumer registration.
   *
   * <p>Implementations are expected to decorate the appropriate {@link Consumer} callbacks. The
   * original {@link Consumer} behavior should not be changed though.
   *
   * @param queue
   * @param consumerTag
   * @param consumer
   * @return
   */
  Consumer basicConsume(String queue, String consumerTag, Consumer consumer);

  /**
   * Decorate message polling with <code>basic.get</code>.
   *
   * <p>Implementations are expected to {@link BasicGetCall#basicGet( BasicGetCall, String)} and
   * return the same result.
   *
   * @param call
   * @param queue
   * @return
   */
  GetResponse basicGet(BasicGetCall call, String queue);

  /** Underlying publishing call. */
  interface PublishCall {

    void publish(AMQP.BasicProperties properties) throws IOException;
  }

  /** Underlying <code>basic.get</code> call. */
  interface BasicGetCall {

    GetResponse get();
  }

  /** Connection information. */
  interface ConnectionInfo {

    String getPeerAddress();

    int getPeerPort();
  }
}
