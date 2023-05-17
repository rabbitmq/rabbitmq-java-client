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
import io.micrometer.observation.ObservationRegistry;

/**
 * Builder to configure and create <a href="https://micrometer.io/docs/observation">Micrometer
 * Observation</a> implementation of {@link ObservationCollector}.
 *
 * @since 5.18.0
 */
public class MicrometerObservationCollectorBuilder {

  private ObservationRegistry registry = ObservationRegistry.NOOP;
  private PublishObservationConvention customPublishObservationConvention;
  private PublishObservationConvention defaultPublishObservationConvention =
      new DefaultPublishObservationConvention();
  private DeliverObservationConvention customProcessObservationConvention;
  private DeliverObservationConvention defaultProcessObservationConvention =
      new DefaultDeliverObservationConvention("rabbitmq.process", "process");
  private DeliverObservationConvention customReceiveObservationConvention;
  private DeliverObservationConvention defaultReceiveObservationConvention =
      new DefaultDeliverObservationConvention("rabbitmq.receive", "receive");

  public MicrometerObservationCollectorBuilder registry(ObservationRegistry registry) {
    this.registry = registry;
    return this;
  }

  public MicrometerObservationCollectorBuilder customPublishObservationConvention(
      PublishObservationConvention customPublishObservationConvention) {
    this.customPublishObservationConvention = customPublishObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder defaultPublishObservationConvention(
      PublishObservationConvention defaultPublishObservationConvention) {
    this.defaultPublishObservationConvention = defaultPublishObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder customProcessObservationConvention(
      DeliverObservationConvention customConsumeObservationConvention) {
    this.customProcessObservationConvention = customConsumeObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder defaultProcessObservationConvention(
      DeliverObservationConvention defaultConsumeObservationConvention) {
    this.defaultProcessObservationConvention = defaultConsumeObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder customReceiveObservationConvention(
      DeliverObservationConvention customReceiveObservationConvention) {
    this.customReceiveObservationConvention = customReceiveObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder defaultReceiveObservationConvention(
      DeliverObservationConvention defaultReceiveObservationConvention) {
    this.defaultReceiveObservationConvention = defaultReceiveObservationConvention;
    return this;
  }

  public ObservationCollector build() {
    return new MicrometerObservationCollector(
        this.registry,
        this.customPublishObservationConvention,
        this.defaultPublishObservationConvention,
        this.customProcessObservationConvention,
        this.defaultProcessObservationConvention,
        this.customReceiveObservationConvention,
        this.defaultReceiveObservationConvention);
  }
}
