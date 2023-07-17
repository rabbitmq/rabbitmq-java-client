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
package com.rabbitmq.client.observation.micrometer;

import com.rabbitmq.client.observation.ObservationCollector;
import io.micrometer.observation.transport.SenderContext;
import java.util.Map;

/**
 * {@link io.micrometer.observation.Observation.Context} for use with RabbitMQ client {@link
 * io.micrometer.observation.Observation} instrumentation.
 *
 * @since 5.19.0
 */
public class PublishContext extends SenderContext<Map<String, Object>> {

  private final String exchange;
  private final String routingKey;
  private final int payloadSizeBytes;
  private final ObservationCollector.ConnectionInfo connectionInfo;

  PublishContext(
      String exchange,
      String routingKey,
      Map<String, Object> headers,
      int payloadSizeBytes,
      ObservationCollector.ConnectionInfo connectionInfo) {
    super((hdrs, key, value) -> hdrs.put(key, value));
    this.exchange = exchange;
    this.routingKey = routingKey;
    this.payloadSizeBytes = payloadSizeBytes;
    this.connectionInfo = connectionInfo;
    setCarrier(headers);
  }

  public String getExchange() {
    return this.exchange;
  }

  public String getRoutingKey() {
    return this.routingKey;
  }

  public int getPayloadSizeBytes() {
    return this.payloadSizeBytes;
  }

  public ObservationCollector.ConnectionInfo getConnectionInfo() {
    return this.connectionInfo;
  }
}
