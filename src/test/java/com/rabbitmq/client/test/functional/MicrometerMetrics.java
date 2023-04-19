// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

import java.io.IOException;
import java.time.Duration;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.SampleTestRunner;
import org.junit.jupiter.api.Nested;

import static com.rabbitmq.client.test.TestUtils.waitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

public class MicrometerMetrics extends BrokerTestCase {

    static final String QUEUE = "metrics.queue";

    @Override
    protected void createResources() throws IOException {
        channel.queueDeclare(QUEUE, true, false, false, null);
    }

    @Override
    protected void releaseResources() throws IOException {
        channel.queueDelete(QUEUE);
    }


    @Nested
    class IntegrationTest extends SampleTestRunner {

        @Override
        public TracingSetup[] getTracingSetup() {
            return new TracingSetup[] { TracingSetup.IN_MEMORY_BRAVE, TracingSetup.ZIPKIN_BRAVE };
        }

        @Override
        public SampleTestRunnerConsumer yourCode() throws Exception {
            return (buildingBlocks, meterRegistry) -> {
                ConnectionFactory connectionFactory = createConnectionFactory();
                MicrometerMetricsCollector collector = new MicrometerMetricsCollector(meterRegistry);
                collector.setObservationRegistry(getObservationRegistry());
                connectionFactory.setMetricsCollector(collector);
                Connection connection1 = null;
                try {
                    connection1 = connectionFactory.newConnection();
                    Channel channel = connection1.createChannel();

                    sendMessage(channel);

                    TestingConsumer testingConsumer = new TestingConsumer(channel, buildingBlocks.getTracer(), buildingBlocks.getTracer().currentSpan());
                    channel.basicConsume(QUEUE, true, testingConsumer);
                    waitAtMost(timeout(), () -> testingConsumer.executed);
                    waitAtMost(timeout(), () -> testingConsumer.assertionsPassed);
                    getMeterRegistry().get("rabbit.publish")
                            .tag("messaging.operation", "publish")
                            .tag("messaging.system", "rabbitmq")
                            .timer();
                    getMeterRegistry().get("rabbit.consume")
                            .tag("messaging.operation", "publish")
                            .tag("messaging.system", "rabbitmq")
                            .timer();
                } finally {
                    safeClose(connection1);
                }
            };
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(10);
    }

    private static ConnectionFactory createConnectionFactory() {
        ConnectionFactory connectionFactory = TestUtils.connectionFactory();
        connectionFactory.setAutomaticRecoveryEnabled(false);
        return connectionFactory;
    }

    private void safeClose(Connection connection) {
        if(connection != null) {
            try {
                connection.abort();
            } catch (Exception e) {
                // OK
            }
        }
    }

    private void sendMessage(Channel channel) throws IOException {
        channel.basicPublish("", QUEUE, null, "msg".getBytes("UTF-8"));
    }

    static class TestingConsumer extends DefaultConsumer {

        volatile boolean executed;

        volatile boolean assertionsPassed;

        private final Tracer tracer;

        private final Span rootSpan;

        public TestingConsumer(Channel channel, Tracer tracer, Span rootSpan) {
            super(channel);
            this.tracer = tracer;
            this.rootSpan = rootSpan;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            executed = true;
            assertThat(tracer.currentSpan()).as("Span must be put in scope").isNotNull();
            assertThat(tracer.currentSpan().context().traceId()).as("Trace id must be propagated").isEqualTo(rootSpan.context().traceId());
            System.out.println("Current span [" + tracer.currentSpan() + "]");
            assertionsPassed = true;
        }
    }

}
