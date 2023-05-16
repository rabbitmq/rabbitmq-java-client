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

import com.rabbitmq.client.observation.micrometer.RabbitMqObservationDocumentation.HighCardinalityTags;
import com.rabbitmq.client.observation.micrometer.RabbitMqObservationDocumentation.LowCardinalityTags;
import io.micrometer.common.KeyValues;
import io.micrometer.common.util.StringUtils;

/**
 * Default implementation of {@link PublishObservationConvention}.
 *
 * @since 5.18.0
 * @see RabbitMqObservationDocumentation
 */
public class DefaultPublishObservationConvention implements PublishObservationConvention {

  private final String name;

  public DefaultPublishObservationConvention() {
    this("rabbitmq.publish");
  }

  public DefaultPublishObservationConvention(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getContextualName(PublishContext context) {
    return exchange(context.getRoutingKey()) + " publish";
  }

  private String exchange(String destination) {
    return StringUtils.isNotBlank(destination) ? destination : "amq.default";
  }

  @Override
  public KeyValues getLowCardinalityKeyValues(PublishContext context) {
    return KeyValues.of(
        LowCardinalityTags.MESSAGING_OPERATION.withValue("publish"),
        LowCardinalityTags.MESSAGING_SYSTEM.withValue("rabbitmq"),
        LowCardinalityTags.NET_PROTOCOL_NAME.withValue("amqp"),
        LowCardinalityTags.NET_PROTOCOL_VERSION.withValue("0.9.1"));
  }

  @Override
  public KeyValues getHighCardinalityKeyValues(PublishContext context) {
    return KeyValues.of(
        HighCardinalityTags.MESSAGING_ROUTING_KEY.withValue(context.getRoutingKey()),
        HighCardinalityTags.MESSAGING_DESTINATION_NAME.withValue(exchange(context.getExchange())),
        HighCardinalityTags.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.withValue(
            String.valueOf(context.getPayloadSizeBytes())),
        HighCardinalityTags.NET_SOCK_PEER_ADDR.withValue(
            context.getConnectionInfo().getPeerAddress()),
        HighCardinalityTags.NET_SOCK_PEER_PORT.withValue(
            String.valueOf(context.getConnectionInfo().getPeerPort())));
  }
}
