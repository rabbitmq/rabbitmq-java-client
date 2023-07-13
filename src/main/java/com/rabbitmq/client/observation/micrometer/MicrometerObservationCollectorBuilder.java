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
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;

/**
 * Builder to configure and create <a href="https://micrometer.io/docs/observation">Micrometer
 * Observation</a> implementation of {@link ObservationCollector}.
 *
 * @since 5.19.0
 */
public class MicrometerObservationCollectorBuilder {

  private ObservationRegistry registry = ObservationRegistry.NOOP;
  private PublishObservationConvention customPublishObservationConvention;
  private PublishObservationConvention defaultPublishObservationConvention =
      new DefaultPublishObservationConvention();
  private DeliverObservationConvention customProcessObservationConvention;
  private DeliverObservationConvention defaultProcessObservationConvention =
      new DefaultProcessObservationConvention("process");
  private DeliverObservationConvention customReceiveObservationConvention;
  private DeliverObservationConvention defaultReceiveObservationConvention =
      new DefaultReceiveObservationConvention("receive");
  private boolean keepObservationStartedOnBasicGet = false;

  /**
   * Set the {@link ObservationRegistry} to use.
   *
   * @param registry the registry
   * @return this builder instance
   */
  public MicrometerObservationCollectorBuilder registry(ObservationRegistry registry) {
    this.registry = registry;
    return this;
  }

  /**
   * Custom convention for <code>basic.publish</code>.
   *
   * <p>If not null, it will override any pre-configured conventions.
   *
   * @param customPublishObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder customPublishObservationConvention(
      PublishObservationConvention customPublishObservationConvention) {
    this.customPublishObservationConvention = customPublishObservationConvention;
    return this;
  }

  /**
   * Default convention for <code>basic.publish</code>.
   *
   * <p>It will be picked if there was neither custom convention nor a pre-configured one via {@link
   * ObservationRegistry}.
   *
   * @param defaultPublishObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder defaultPublishObservationConvention(
      PublishObservationConvention defaultPublishObservationConvention) {
    this.defaultPublishObservationConvention = defaultPublishObservationConvention;
    return this;
  }

  /**
   * Custom convention for <code>basic.deliver</code>.
   *
   * <p>If not null, it will override any pre-configured conventions.
   *
   * @param customProcessObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder customProcessObservationConvention(
      DeliverObservationConvention customProcessObservationConvention) {
    this.customProcessObservationConvention = customProcessObservationConvention;
    return this;
  }

  /**
   * Default convention for <code>basic.delivery</code>.
   *
   * <p>It will be picked if there was neither custom convention nor a pre-configured one via {@link
   * ObservationRegistry}.
   *
   * @param defaultProcessObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder defaultProcessObservationConvention(
      DeliverObservationConvention defaultProcessObservationConvention) {
    this.defaultProcessObservationConvention = defaultProcessObservationConvention;
    return this;
  }

  /**
   * Custom convention for <code>basic.get</code>.
   *
   * <p>If not null, it will override any pre-configured conventions.
   *
   * @param customReceiveObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder customReceiveObservationConvention(
      DeliverObservationConvention customReceiveObservationConvention) {
    this.customReceiveObservationConvention = customReceiveObservationConvention;
    return this;
  }

  /**
   * Default convention for <code>basic.get</code>.
   *
   * <p>It will be picked if there was neither custom convention nor a pre-configured one via {@link
   * ObservationRegistry}.
   *
   * @param defaultReceiveObservationConvention the convention
   * @return this builder instance
   * @see io.micrometer.observation.docs.ObservationDocumentation#observation(ObservationConvention,
   *     ObservationConvention, Supplier, ObservationRegistry)
   */
  public MicrometerObservationCollectorBuilder defaultReceiveObservationConvention(
      DeliverObservationConvention defaultReceiveObservationConvention) {
    this.defaultReceiveObservationConvention = defaultReceiveObservationConvention;
    return this;
  }

  /**
   * Whether to keep the <code>basic.get</code> observation started or not.
   *
   * <p>The {@link MicrometerObservationCollector} starts and stops the observation immediately
   * after the message reception. This way the observation can have all the context from the
   * received message but has a very short duration. This is the default behavior.
   *
   * <p>By setting this flag to <code>true</code> the collector does not stop the observation and
   * opens a scope. The processing of the message can then be included in the observation.
   *
   * <p>This is then the responsibility of the developer to retrieve the observation and stop it to
   * avoid memory leaks. Here is an example:
   *
   * <pre>
   * GetResponse response = channel.basicGet(queue, true);
   * // process the message...
   * // stop the observation
   * Observation.Scope scope = observationRegistry.getCurrentObservationScope();
   * scope.close();
   * scope.getCurrentObservation().stop();</pre>
   *
   * Default is false, that is stopping the observation immediately.
   *
   * @param keepObservationStartedOnBasicGet whether to keep the observation started or not
   * @return this builder instance
   */
  public MicrometerObservationCollectorBuilder keepObservationStartedOnBasicGet(
      boolean keepObservationStartedOnBasicGet) {
    this.keepObservationStartedOnBasicGet = keepObservationStartedOnBasicGet;
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
        this.defaultReceiveObservationConvention,
        keepObservationStartedOnBasicGet);
  }
}
