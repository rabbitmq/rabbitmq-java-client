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

import com.rabbitmq.client.*;
import com.rabbitmq.client.observation.ObservationCollector;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class MicrometerObservationCollector implements ObservationCollector {

  private final ObservationRegistry registry;

  private final PublishObservationConvention customPublishConvention, defaultPublishConvention;
  private final DeliverObservationConvention customProcessConvention, defaultProcessConvention;
  private final DeliverObservationConvention customReceiveConvention, defaultReceiveConvention;
  private final boolean keepObservationOpenOnBasicGet;

  MicrometerObservationCollector(
      ObservationRegistry registry,
      PublishObservationConvention customPublishConvention,
      PublishObservationConvention defaultPublishConvention,
      DeliverObservationConvention customProcessConvention,
      DeliverObservationConvention defaultProcessConvention,
      DeliverObservationConvention customReceiveConvention,
      DeliverObservationConvention defaultReceiveConvention,
      boolean keepObservationOpenOnBasicGet) {
    this.registry = registry;
    this.customPublishConvention = customPublishConvention;
    this.defaultPublishConvention = defaultPublishConvention;
    this.customProcessConvention = customProcessConvention;
    this.defaultProcessConvention = defaultProcessConvention;
    this.customReceiveConvention = customReceiveConvention;
    this.defaultReceiveConvention = defaultReceiveConvention;
    this.keepObservationOpenOnBasicGet = keepObservationOpenOnBasicGet;
  }

  @Override
  public void publish(
      PublishCall call,
      AMQP.Basic.Publish publish,
      AMQP.BasicProperties properties,
      byte[] body,
      ConnectionInfo connectionInfo)
      throws IOException {
    Map<String, Object> headers;
    if (properties.getHeaders() == null) {
      headers = new HashMap<>();
    } else {
      headers = new HashMap<>(properties.getHeaders());
    }
    PublishContext micrometerPublishContext =
        new PublishContext(
            publish.getExchange(),
            publish.getRoutingKey(),
            headers,
            body == null ? 0 : body.length,
            connectionInfo);
    AMQP.BasicProperties.Builder builder = properties.builder();
    builder.headers(headers);
    Observation observation =
        RabbitMqObservationDocumentation.PUBLISH_OBSERVATION.observation(
            this.customPublishConvention,
            this.defaultPublishConvention,
            () -> micrometerPublishContext,
            registry);
    observation.start();
    try {
      call.publish(builder.build());
    } catch (IOException | AlreadyClosedException e) {
      observation.error(e);
      throw e;
    } finally {
      observation.stop();
    }
  }

  @Override
  public Consumer basicConsume(String queue, String consumerTag, Consumer consumer) {
    return new ObservationConsumer(
        queue,
        consumer,
        this.registry,
        this.customProcessConvention,
        this.defaultProcessConvention);
  }

  @Override
  public GetResponse basicGet(BasicGetCall call, String queue) {
    Observation observation =
        Observation.createNotStarted("rabbitmq.receive", registry)
            .highCardinalityKeyValues(
                KeyValues.of(
                    RabbitMqObservationDocumentation.LowCardinalityTags.MESSAGING_OPERATION
                        .withValue("receive"),
                    RabbitMqObservationDocumentation.LowCardinalityTags.MESSAGING_SYSTEM.withValue(
                        "rabbitmq")))
            .start();
    boolean stopped = false;
    try {
      GetResponse response = call.get();
      if (response != null) {
        observation.stop();
        stopped = true;
        Map<String, Object> headers;
        if (response.getProps() == null || response.getProps().getHeaders() == null) {
          headers = Collections.emptyMap();
        } else {
          headers = response.getProps().getHeaders();
        }
        DeliverContext context =
            new DeliverContext(
                response.getEnvelope().getExchange(),
                response.getEnvelope().getRoutingKey(),
                queue,
                headers,
                response.getBody() == null ? 0 : response.getBody().length);
        Observation receiveObservation =
            RabbitMqObservationDocumentation.RECEIVE_OBSERVATION.observation(
                customReceiveConvention, defaultReceiveConvention, () -> context, registry);
        receiveObservation.start();
        if (this.keepObservationOpenOnBasicGet) {
          receiveObservation.openScope();
        } else {
          receiveObservation.stop();
        }
      }
      return response;
    } catch (RuntimeException e) {
      observation.error(e);
      throw e;
    } finally {
      if (!stopped) {
        observation.stop();
      }
    }
  }

  private static class ObservationConsumer implements Consumer {

    private final String queue;
    private final Consumer delegate;

    private final ObservationRegistry observationRegistry;

    private final DeliverObservationConvention customConsumeConvention, defaultConsumeConvention;

    private ObservationConsumer(
        String queue,
        Consumer delegate,
        ObservationRegistry observationRegistry,
        DeliverObservationConvention customConsumeConvention,
        DeliverObservationConvention defaultConsumeConvention) {
      this.queue = queue;
      this.delegate = delegate;
      this.observationRegistry = observationRegistry;
      this.customConsumeConvention = customConsumeConvention;
      this.defaultConsumeConvention = defaultConsumeConvention;
    }

    @Override
    public void handleConsumeOk(String consumerTag) {
      delegate.handleConsumeOk(consumerTag);
    }

    @Override
    public void handleCancelOk(String consumerTag) {
      delegate.handleCancelOk(consumerTag);
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
      delegate.handleCancel(consumerTag);
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
      delegate.handleShutdownSignal(consumerTag, sig);
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
      delegate.handleRecoverOk(consumerTag);
    }

    @Override
    public void handleDelivery(
        String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
        throws IOException {
      Map<String, Object> headers;
      if (properties == null || properties.getHeaders() == null) {
        headers = Collections.emptyMap();
      } else {
        headers = properties.getHeaders();
      }
      DeliverContext context =
          new DeliverContext(
              envelope.getExchange(),
              envelope.getRoutingKey(),
              queue,
              headers,
              body == null ? 0 : body.length);
      Observation observation =
          RabbitMqObservationDocumentation.PROCESS_OBSERVATION.observation(
              customConsumeConvention,
              defaultConsumeConvention,
              () -> context,
              observationRegistry);
      observation.observeChecked(
          () -> delegate.handleDelivery(consumerTag, envelope, properties, body));
    }
  }
}
