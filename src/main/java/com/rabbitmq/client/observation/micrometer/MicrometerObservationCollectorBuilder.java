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
 * @since 5.18.0
 */
public class MicrometerObservationCollectorBuilder {

  private ObservationRegistry registry = ObservationRegistry.NOOP;
  private PublishObservationConvention customPublishObservationConvention;
  private PublishObservationConvention defaultPublishObservationConvention =
      new DefaultPublishObservationConvention();
  private ConsumeObservationConvention customConsumeObservationConvention;
  private ConsumeObservationConvention defaultConsumeObservationConvention =
      new DefaultConsumeObservationConvention();

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

  public MicrometerObservationCollectorBuilder customConsumeObservationConvention(
      ConsumeObservationConvention customConsumeObservationConvention) {
    this.customConsumeObservationConvention = customConsumeObservationConvention;
    return this;
  }

  public MicrometerObservationCollectorBuilder defaultConsumeObservationConvention(
      ConsumeObservationConvention defaultConsumeObservationConvention) {
    this.defaultConsumeObservationConvention = defaultConsumeObservationConvention;
    return this;
  }

  public ObservationCollector build() {
    return new MicrometerObservationCollector(
      this.registry,
      this.customPublishObservationConvention,
      this.defaultPublishObservationConvention,
      this.customConsumeObservationConvention,
      this.defaultConsumeObservationConvention
    );
  }
}
