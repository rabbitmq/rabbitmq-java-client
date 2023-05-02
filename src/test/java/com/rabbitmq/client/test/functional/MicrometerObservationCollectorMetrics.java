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

package com.rabbitmq.client.test.functional;

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.*;
import com.rabbitmq.client.observation.ObservationCollector;
import com.rabbitmq.client.observation.micrometer.MicrometerObservationCollectorBuilder;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.SampleTestRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MicrometerObservationCollectorMetrics extends BrokerTestCase {

  static final String QUEUE = "metrics.queue";

  private static ConnectionFactory createConnectionFactory() {
    ConnectionFactory connectionFactory = TestUtils.connectionFactory();
    connectionFactory.setAutomaticRecoveryEnabled(true);
    return connectionFactory;
  }

  private static Consumer consumer(DeliverCallback callback) {
    return new Consumer() {
      @Override
      public void handleConsumeOk(String consumerTag) {}

      @Override
      public void handleCancelOk(String consumerTag) {}

      @Override
      public void handleCancel(String consumerTag) throws IOException {}

      @Override
      public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {}

      @Override
      public void handleRecoverOk(String consumerTag) {}

      @Override
      public void handleDelivery(
          String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
          throws IOException {
        callback.handle(consumerTag, new Delivery(envelope, properties, body));
      }
    };
  }

  @Override
  protected void createResources() throws IOException {
    channel.queueDeclare(QUEUE, true, false, false, null);
  }

  @Override
  protected void releaseResources() throws IOException {
    channel.queueDelete(QUEUE);
  }

  private Duration timeout() {
    return Duration.ofSeconds(10);
  }

  private void safeClose(Connection connection) {
    if (connection != null) {
      try {
        connection.abort();
      } catch (Exception e) {
        // OK
      }
    }
  }

  private void sendMessage(Channel channel) throws IOException {
    channel.basicPublish("", QUEUE, null, "msg".getBytes(StandardCharsets.UTF_8));
  }

  @Nested
  class IntegrationTest extends SampleTestRunner {

    @Override
    public TracingSetup[] getTracingSetup() {
      return new TracingSetup[] {TracingSetup.IN_MEMORY_BRAVE, TracingSetup.ZIPKIN_BRAVE};
    }

    @Test
    void test() {}

    @Override
    public SampleTestRunnerConsumer yourCode() {
      return (buildingBlocks, meterRegistry) -> {
        ConnectionFactory connectionFactory = createConnectionFactory();
        ObservationCollector collector =
            new MicrometerObservationCollectorBuilder().registry(getObservationRegistry()).build();
        connectionFactory.setObservationCollector(collector);
        Connection publishConnection = null, consumeConnection = null;
        try {
          publishConnection = connectionFactory.newConnection();
          Channel channel = publishConnection.createChannel();

          sendMessage(channel);

          Tracer tracer = buildingBlocks.getTracer();
          Span rootSpan = buildingBlocks.getTracer().currentSpan();
          CountDownLatch consumeLatch = new CountDownLatch(1);
          Consumer consumer =
              consumer(
                  (consumerTag, message) -> {
                    assertThat(tracer.currentSpan()).as("Span must be put in scope").isNotNull();
                    assertThat(tracer.currentSpan().context().traceId())
                        .as("Trace id must be propagated")
                        .isEqualTo(rootSpan.context().traceId());
                    System.out.println("Current span [" + tracer.currentSpan() + "]");
                    consumeLatch.countDown();
                  });

          consumeConnection = connectionFactory.newConnection();
          channel = consumeConnection.createChannel();
          channel.basicConsume(QUEUE, true, consumer);

          assertThat(consumeLatch.await(10, TimeUnit.SECONDS)).isTrue();
          waitAtMost(() -> getMeterRegistry().find("rabbitmq.publish").timer() != null &&
              getMeterRegistry().find("rabbitmq.consume").timer() != null);
          getMeterRegistry()
              .get("rabbitmq.publish")
              .tag("messaging.operation", "publish")
              .tag("messaging.system", "rabbitmq")
              .timer();
          getMeterRegistry()
              .get("rabbitmq.consume")
              .tag("messaging.operation", "publish")
              .tag("messaging.system", "rabbitmq")
              .timer();
        } finally {
          safeClose(publishConnection);
          safeClose(consumeConnection);
        }
      };
    }
  }
}
