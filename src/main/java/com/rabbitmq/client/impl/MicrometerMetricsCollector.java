// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.ShutdownSignalException;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.*;

/**
 * Micrometer implementation of {@link MetricsCollector}.
 * Note transactions are not supported (see {@link MetricsCollector}.
 * Micrometer provides out-of-the-box support for report backends like JMX,
 * Graphite, Ganglia, Atlas, Datadog, etc. See Micrometer documentation for
 * more details.
 *
 * Note Micrometer requires Java 8 or more, so does this {@link MetricsCollector}.
 *
 * @see MetricsCollector
 * @since 4.3.0
 */
public class MicrometerMetricsCollector extends AbstractMetricsCollector {

    private static final String MICROMETER_OBSERVATION_KEY = "micrometer.observation";

    private final AtomicLong connections;

    private final AtomicLong channels;

    private final Counter publishedMessages;

    private final Counter failedToPublishMessages;

    private final Counter ackedPublishedMessages;

    private final Counter nackedPublishedMessages;

    private final Counter unroutedPublishedMessages;

    private final Counter consumedMessages;

    private final Counter acknowledgedMessages;

    private final Counter rejectedMessages;

    private MicrometerPublishObservationConvention publishObservationConvention;

    private MicrometerConsumeObservationConvention consumeObservationConvention;

    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

    public MicrometerMetricsCollector(MeterRegistry registry) {
        this(registry, "rabbitmq");
    }

    public MicrometerMetricsCollector(final MeterRegistry registry, final String prefix) {
        this(registry, prefix, Collections.emptyList());
    }

    public MicrometerMetricsCollector(final MeterRegistry registry, final String prefix, final String ... tags) {
        this(registry, prefix, Tags.of(tags));
    }

    public MicrometerMetricsCollector(final MeterRegistry registry, final String prefix, final Iterable<Tag> tags) {
        this(metric -> metric.create(registry, prefix, tags));
    }

    public MicrometerMetricsCollector(Function<Metrics, Object> metricsCreator) {
        this.connections = (AtomicLong) metricsCreator.apply(CONNECTIONS);
        this.channels = (AtomicLong) metricsCreator.apply(CHANNELS);
        this.publishedMessages = (Counter) metricsCreator.apply(PUBLISHED_MESSAGES);
        this.consumedMessages = (Counter) metricsCreator.apply(CONSUMED_MESSAGES);
        this.acknowledgedMessages = (Counter) metricsCreator.apply(ACKNOWLEDGED_MESSAGES);
        this.rejectedMessages = (Counter) metricsCreator.apply(REJECTED_MESSAGES);
        this.failedToPublishMessages = (Counter) metricsCreator.apply(FAILED_TO_PUBLISH_MESSAGES);
        this.ackedPublishedMessages = (Counter) metricsCreator.apply(ACKED_PUBLISHED_MESSAGES);
        this.nackedPublishedMessages = (Counter) metricsCreator.apply(NACKED_PUBLISHED_MESSAGES);
        this.unroutedPublishedMessages = (Counter) metricsCreator.apply(UNROUTED_PUBLISHED_MESSAGES);
    }

    @Override
    protected void incrementConnectionCount(Connection connection) {
        connections.incrementAndGet();
    }

    @Override
    protected void decrementConnectionCount(Connection connection) {
        connections.decrementAndGet();
    }

    @Override
    protected void incrementChannelCount(Channel channel) {
        channels.incrementAndGet();
    }

    @Override
    protected void decrementChannelCount(Channel channel) {
        channels.decrementAndGet();
    }

    @Override
    protected void markPublishedMessage() {
        publishedMessages.increment();
    }

    @Override
    protected void markMessagePublishFailed() {
        failedToPublishMessages.increment();
    }

    @Override
    protected void markConsumedMessage() {
        consumedMessages.increment();
    }

    @Override
    protected void markAcknowledgedMessage() {
        acknowledgedMessages.increment();
    }

    @Override
    protected void markRejectedMessage() {
        rejectedMessages.increment();
    }

    @Override
    protected void markMessagePublishAcknowledged() {
        ackedPublishedMessages.increment();
    }

    @Override
    protected void markMessagePublishNotAcknowledged() {
        nackedPublishedMessages.increment();
    }

    @Override
    protected void markPublishedMessageUnrouted() {
        unroutedPublishedMessages.increment();
    }

    @Override
    public void basicPrePublish(Channel channel, long deliveryTag, PublishArguments publishArguments) {
        if (observationRegistry.isNoop()) {
            return;
        }
        // TODO: Is this for fire and forget or request reply too? If r-r then we have to have 2 contexts
        MicrometerPublishContext micrometerPublishContext = new MicrometerPublishContext(publishArguments);
        Observation observation = MicrometerRabbitMqObservationDocumentation.PUBLISH_OBSERVATION.observation(this.publishObservationConvention, DefaultMicrometerPublishObservationConvention.INSTANCE, () -> micrometerPublishContext, observationRegistry);
        publishArguments.getContext().put(MICROMETER_OBSERVATION_KEY, observation.start());
        publishArguments.setProps(micrometerPublishContext.getPropertiesBuilder().build());
    }

    @Override
    public void basicPublishFailure(Channel channel, Exception exception, PublishArguments publishArguments) {
        if (observationRegistry.isNoop()) {
            super.basicPublishFailure(channel, exception);  // TODO: Do we want both the observation and the metrics?
            return;
        }
        Observation observation = getObservation(publishArguments);
        if (observation == null) {
            return;
        }
        observation.error(exception);
    }

    @Override
    public void basicPublish(Channel channel, long deliveryTag, PublishArguments publishArguments) {
        if (observationRegistry.isNoop()) {
            super.basicPublish(channel, deliveryTag); // TODO: Do we want both the observation and the metrics?
            return;
        }
        Observation observation = getObservation(publishArguments);
        if (observation == null) {
            return;
        }
        observation.stop();
    }

    @Override
    public Consumer basicPreConsume(Channel channel, String consumerTag, boolean autoAck, AMQCommand amqCommand, Consumer callback) {
        return new ObservationConsumer(callback, observationRegistry, consumeObservationConvention);
    }

    private static Observation getObservation(PublishArguments publishArguments) {
        return (Observation) publishArguments.getContext().get(MICROMETER_OBSERVATION_KEY);
    }

    public AtomicLong getConnections() {
        return connections;
    }

    public AtomicLong getChannels() {
        return channels;
    }

    public Counter getPublishedMessages() {
        return publishedMessages;
    }

    public Counter getFailedToPublishMessages() {
        return failedToPublishMessages;
    }

    public Counter getAckedPublishedMessages() {
        return ackedPublishedMessages;
    }

    public Counter getNackedPublishedMessages() {
        return nackedPublishedMessages;
    }

    public Counter getUnroutedPublishedMessages() {
        return unroutedPublishedMessages;
    }

    public Counter getConsumedMessages() {
        return consumedMessages;
    }

    public Counter getAcknowledgedMessages() {
        return acknowledgedMessages;
    }

    public Counter getRejectedMessages() {
        return rejectedMessages;
    }

    public void setPublishObservationConvention(MicrometerPublishObservationConvention publishObservationConvention) {
        this.publishObservationConvention = publishObservationConvention;
    }

    public void setConsumeObservationConvention(MicrometerConsumeObservationConvention consumeObservationConvention) {
        this.consumeObservationConvention = consumeObservationConvention;
    }

    public void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public enum Metrics {
        CONNECTIONS {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.gauge(prefix + ".connections", tags, new AtomicLong(0));
            }
        },
        CHANNELS {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.gauge(prefix + ".channels", tags, new AtomicLong(0));
            }
        },
        PUBLISHED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".published", tags);
            }
        },
        CONSUMED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".consumed", tags);
            }
        },
        ACKNOWLEDGED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".acknowledged", tags);
            }
        },
        REJECTED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".rejected", tags);
            }
        },
        FAILED_TO_PUBLISH_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".failed_to_publish", tags);
            }
        },
        ACKED_PUBLISHED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".acknowledged_published", tags);
            }
        },
        NACKED_PUBLISHED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".not_acknowledged_published", tags);
            }
        },
        UNROUTED_PUBLISHED_MESSAGES {
            @Override
            Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags) {
                return registry.counter(prefix + ".unrouted_published", tags);
            }
        };

        /**
         *
         * @param registry
         * @param prefix
         * @deprecated will be removed in 6.0.0
         */
        @Deprecated
        Object create(MeterRegistry registry, String prefix) {
            return this.create(registry, prefix, Collections.emptyList());
        }

        abstract Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags);

    }

    private static class ObservationConsumer implements Consumer {

        private final Consumer delegate;

        private final ObservationRegistry observationRegistry;

        private final MicrometerConsumeObservationConvention observationConvention;

        ObservationConsumer(Consumer delegate, ObservationRegistry observationRegistry, @Nullable MicrometerConsumeObservationConvention observationConvention) {
            this.delegate = delegate;
            this.observationRegistry = observationRegistry;
            this.observationConvention = observationConvention;
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
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            MicrometerConsumeContext context = new MicrometerConsumeContext(consumerTag, envelope, properties, body);
            Observation observation = MicrometerRabbitMqObservationDocumentation.CONSUME_OBSERVATION.observation(observationConvention, DefaultMicrometerConsumeObservationConvention.INSTANCE, () -> context, observationRegistry);
            observation.observeChecked(() -> delegate.handleDelivery(consumerTag, envelope, properties, body));
        }
    }

}
