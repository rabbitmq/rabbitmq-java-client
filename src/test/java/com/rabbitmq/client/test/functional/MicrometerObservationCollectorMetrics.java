// Copyright (c) 2023 Broadcom. All Rights Reserved.
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

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.*;
import com.rabbitmq.client.observation.ObservationCollector;
import com.rabbitmq.client.observation.micrometer.MicrometerObservationCollectorBuilder;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import io.micrometer.observation.NullObservation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.simple.SpanAssert;
import io.micrometer.tracing.test.simple.SpansAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Nested;

public class MicrometerObservationCollectorMetrics extends BrokerTestCase {

  static final String QUEUE = "metrics.queue";
  private static final byte[] PAYLOAD = "msg".getBytes(StandardCharsets.UTF_8);

  private static ConnectionFactory createConnectionFactory() {
    return createConnectionFactory(null);
  }

  private static ConnectionFactory createConnectionFactory(
      ObservationRegistry observationRegistry) {
    return createConnectionFactory(false, observationRegistry);
  }

  private static ConnectionFactory createConnectionFactory(
      boolean keepObservationStartedOnBasicGet, ObservationRegistry observationRegistry) {
    ConnectionFactory connectionFactory = TestUtils.connectionFactory();
    connectionFactory.setAutomaticRecoveryEnabled(true);
    if (observationRegistry != null) {
      ObservationCollector collector =
          new MicrometerObservationCollectorBuilder()
              .keepObservationStartedOnBasicGet(keepObservationStartedOnBasicGet)
              .registry(observationRegistry)
              .build();
      connectionFactory.setObservationCollector(collector);
    }
    return connectionFactory;
  }

  private static Consumer consumer(DeliverCallback callback) {
    return new Consumer() {
      @Override
      public void handleConsumeOk(String consumerTag) {}

      @Override
      public void handleCancelOk(String consumerTag) {}

      @Override
      public void handleCancel(String consumerTag) {}

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

  private void safeClose(Connection connection) {
    if (connection != null) {
      try {
        connection.abort(10_000);
      } catch (Exception e) {
        // OK
      }
    }
  }

  private void sendMessage(Channel channel) throws IOException {
    channel.basicPublish("", QUEUE, null, PAYLOAD);
  }

  private abstract static class IntegrationTest extends SampleTestRunner {

    @Override
    public TracingSetup[] getTracingSetup() {
      return new TracingSetup[] {TracingSetup.IN_MEMORY_BRAVE, TracingSetup.ZIPKIN_BRAVE};
    }
  }

  @Nested
  class PublishConsume extends IntegrationTest {

    @Override
    public SampleTestRunnerConsumer yourCode() {
      return (buildingBlocks, meterRegistry) -> {
        ConnectionFactory connectionFactory = createConnectionFactory(getObservationRegistry());
        Connection publishConnection = null, consumeConnection = null;
        try {
          publishConnection = connectionFactory.newConnection();
          Channel channel = publishConnection.createChannel();

          sendMessage(channel);

          CountDownLatch consumeLatch = new CountDownLatch(1);
          Consumer consumer = consumer((consumerTag, message) -> consumeLatch.countDown());

          consumeConnection = connectionFactory.newConnection();
          channel = consumeConnection.createChannel();
          channel.basicConsume(QUEUE, true, consumer);

          assertThat(consumeLatch.await(10, TimeUnit.SECONDS)).isTrue();
          waitAtMost(() -> buildingBlocks.getFinishedSpans().size() == 2);
          SpansAssert.assertThat(buildingBlocks.getFinishedSpans()).haveSameTraceId().hasSize(2);
          SpanAssert.assertThat(buildingBlocks.getFinishedSpans().get(0))
              .hasNameEqualTo("metrics.queue publish")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTagWithKey("net.sock.peer.addr")
              .hasTag("net.sock.peer.port", "5672")
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          SpanAssert.assertThat(buildingBlocks.getFinishedSpans().get(1))
              .hasNameEqualTo("metrics.queue process")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.source.name", "metrics.queue")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          waitAtMost(
              () ->
                  getMeterRegistry().find("rabbitmq.publish").timer() != null
                      && getMeterRegistry().find("rabbitmq.process").timer() != null);
          getMeterRegistry()
              .get("rabbitmq.publish")
              .tag("messaging.operation", "publish")
              .tag("messaging.system", "rabbitmq")
              .timer();
          getMeterRegistry()
              .get("rabbitmq.process")
              .tag("messaging.operation", "process")
              .tag("messaging.system", "rabbitmq")
              .timer();
        } finally {
          safeClose(publishConnection);
          safeClose(consumeConnection);
        }
      };
    }
  }

  @Nested
  class PublishBasicGet extends IntegrationTest {

    @Override
    public SampleTestRunnerConsumer yourCode() {
      return (buildingBlocks, meterRegistry) -> {
        ObservationRegistry observationRegistry = getObservationRegistry();
        ConnectionFactory connectionFactory = createConnectionFactory(observationRegistry);
        Connection publishConnection = null, consumeConnection = null;
        try {
          publishConnection = connectionFactory.newConnection();
          Channel channel = publishConnection.createChannel();

          new NullObservation(observationRegistry).observeChecked(() -> sendMessage(channel));

          consumeConnection = connectionFactory.newConnection();
          Channel basicGetChannel = consumeConnection.createChannel();
          waitAtMost(() -> basicGetChannel.basicGet(QUEUE, true) != null);
          waitAtMost(() -> buildingBlocks.getFinishedSpans().size() >= 3);

          Map<String, List<FinishedSpan>> finishedSpans =
              buildingBlocks.getFinishedSpans().stream()
                  .collect(Collectors.groupingBy(FinishedSpan::getTraceId));
          BDDAssertions.then(finishedSpans)
              .as("One trace id for sending, one for polling")
              .hasSize(2);
          Collection<List<FinishedSpan>> spans = finishedSpans.values();
          List<FinishedSpan> sendAndReceiveSpans =
              spans.stream()
                  .filter(f -> f.size() == 2)
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new AssertionError(
                              "null_observation (fake nulling observation) -> produce -> consume"));
          sendAndReceiveSpans.sort(Comparator.comparing(FinishedSpan::getStartTimestamp));
          SpanAssert.assertThat(sendAndReceiveSpans.get(0))
              .hasNameEqualTo("metrics.queue publish")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTagWithKey("net.sock.peer.addr")
              .hasTag("net.sock.peer.port", "5672")
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          SpanAssert.assertThat(sendAndReceiveSpans.get(1))
              .hasNameEqualTo("metrics.queue receive")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.source.name", "metrics.queue")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          List<FinishedSpan> pollingSpans =
              spans.stream()
                  .filter(f -> f.size() == 1)
                  .findFirst()
                  .orElseThrow(() -> new AssertionError("rabbitmq.receive (child of test span)"));
          SpanAssert.assertThat(pollingSpans.get(0)).hasNameEqualTo("rabbitmq.receive");
          waitAtMost(
              () ->
                  getMeterRegistry().find("rabbitmq.publish").timer() != null
                      && getMeterRegistry().find("rabbitmq.receive").timer() != null);
          getMeterRegistry()
              .get("rabbitmq.publish")
              .tag("messaging.operation", "publish")
              .tag("messaging.system", "rabbitmq")
              .timer();
          getMeterRegistry()
              .get("rabbitmq.receive")
              .tag("messaging.operation", "receive")
              .tag("messaging.system", "rabbitmq")
              .timer();
        } finally {
          safeClose(publishConnection);
          safeClose(consumeConnection);
        }
      };
    }
  }

  @Nested
  class PublishBasicGetKeepObservationOpen extends IntegrationTest {

    @Override
    public SampleTestRunnerConsumer yourCode() {
      return (buildingBlocks, meterRegistry) -> {
        ObservationRegistry observationRegistry = getObservationRegistry();
        ConnectionFactory connectionFactory = createConnectionFactory(true, observationRegistry);
        Connection publishConnection = null, consumeConnection = null;
        try {
          publishConnection = connectionFactory.newConnection();
          Channel channel = publishConnection.createChannel();

          new NullObservation(observationRegistry).observeChecked(() -> sendMessage(channel));

          consumeConnection = connectionFactory.newConnection();
          Channel basicGetChannel = consumeConnection.createChannel();
          waitAtMost(() -> basicGetChannel.basicGet(QUEUE, true) != null);
          Observation.Scope scope = observationRegistry.getCurrentObservationScope();
          assertThat(scope).isNotNull();
          // creating a dummy span to make sure it's wrapped into the receive one
          buildingBlocks.getTracer().nextSpan().name("foobar").start().end();
          scope.close();
          scope.getCurrentObservation().stop();
          waitAtMost(() -> buildingBlocks.getFinishedSpans().size() >= 3 + 1);
          Map<String, List<FinishedSpan>> finishedSpans =
              buildingBlocks.getFinishedSpans().stream()
                  .collect(Collectors.groupingBy(FinishedSpan::getTraceId));
          BDDAssertions.then(finishedSpans)
              .as("One trace id for sending, one for polling")
              .hasSize(2);
          Collection<List<FinishedSpan>> spans = finishedSpans.values();
          List<FinishedSpan> sendAndReceiveSpans =
              spans.stream()
                  .filter(f -> f.size() == 2 + 1)
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new AssertionError(
                              "null_observation (fake nulling observation) -> produce -> consume"));
          sendAndReceiveSpans.sort(Comparator.comparing(FinishedSpan::getStartTimestamp));
          SpanAssert.assertThat(sendAndReceiveSpans.get(0))
              .hasNameEqualTo("metrics.queue publish")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTagWithKey("net.sock.peer.addr")
              .hasTag("net.sock.peer.port", "5672")
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          SpanAssert.assertThat(sendAndReceiveSpans.get(1))
              .hasNameEqualTo("metrics.queue receive")
              .hasTag("messaging.rabbitmq.destination.routing_key", "metrics.queue")
              .hasTag("messaging.destination.name", "amq.default")
              .hasTag("messaging.source.name", "metrics.queue")
              .hasTag("messaging.message.payload_size_bytes", String.valueOf(PAYLOAD.length))
              .hasTag("net.protocol.name", "amqp")
              .hasTag("net.protocol.version", "0.9.1");
          List<FinishedSpan> pollingSpans =
              spans.stream()
                  .filter(f -> f.size() == 1)
                  .findFirst()
                  .orElseThrow(() -> new AssertionError("rabbitmq.receive (child of test span)"));
          SpanAssert.assertThat(pollingSpans.get(0)).hasNameEqualTo("rabbitmq.receive");
          waitAtMost(
              () ->
                  getMeterRegistry().find("rabbitmq.publish").timer() != null
                      && getMeterRegistry().find("rabbitmq.receive").timer() != null);
          getMeterRegistry()
              .get("rabbitmq.publish")
              .tag("messaging.operation", "publish")
              .tag("messaging.system", "rabbitmq")
              .timer();
          getMeterRegistry()
              .get("rabbitmq.receive")
              .tag("messaging.operation", "receive")
              .tag("messaging.system", "rabbitmq")
              .timer();
        } finally {
          safeClose(publishConnection);
          safeClose(consumeConnection);
        }
      };
    }
  }

  @Nested
  class ConsumeWithoutObservationShouldNotFail extends IntegrationTest {

    @Override
    public SampleTestRunnerConsumer yourCode() {
      return (buildingBlocks, meterRegistry) -> {
        ConnectionFactory publishCf = createConnectionFactory();
        ConnectionFactory consumeCf = createConnectionFactory(getObservationRegistry());
        Connection publishConnection = null, consumeConnection = null;
        try {
          publishConnection = publishCf.newConnection();
          Channel channel = publishConnection.createChannel();

          sendMessage(channel);

          CountDownLatch consumeLatch = new CountDownLatch(1);
          Consumer consumer = consumer((consumerTag, message) -> consumeLatch.countDown());

          consumeConnection = consumeCf.newConnection();
          channel = consumeConnection.createChannel();
          channel.basicConsume(QUEUE, true, consumer);

          assertThat(consumeLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
          safeClose(publishConnection);
          safeClose(consumeConnection);
        }
      };
    }
  }
}
