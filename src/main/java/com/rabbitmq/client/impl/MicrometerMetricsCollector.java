// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.ACKNOWLEDGED_MESSAGES;
import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.CHANNELS;
import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.CONNECTIONS;
import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.CONSUMED_MESSAGES;
import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.PUBLISHED_MESSAGES;
import static com.rabbitmq.client.impl.MicrometerMetricsCollector.Metrics.REJECTED_MESSAGES;

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

    private final AtomicLong connections;

    private final AtomicLong channels;

    private final Counter publishedMessages;

    private final Counter consumedMessages;

    private final Counter acknowledgedMessages;

    private final Counter rejectedMessages;

    public MicrometerMetricsCollector(MeterRegistry registry) {
        this(registry, "rabbitmq");
    }

    public MicrometerMetricsCollector(final MeterRegistry registry, final String prefix) {
        this(registry, prefix, Collections.emptyList());
    }

    public MicrometerMetricsCollector(final MeterRegistry registry, final String prefix, final String ... tags) {
        this(registry, prefix, Tags.zip(tags));
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

    public AtomicLong getConnections() {
        return connections;
    }

    public AtomicLong getChannels() {
        return channels;
    }

    public Counter getPublishedMessages() {
        return publishedMessages;
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
        };

        /**
         *
         * @param registry
         * @param prefix
         * @deprecated will be removed in 6.0.0
         * @return
         */
        @Deprecated
        Object create(MeterRegistry registry, String prefix) {
            return this.create(registry, prefix, Collections.<Tag>emptyList());
        }

        abstract Object create(MeterRegistry registry, String prefix, Iterable<Tag> tags);

    }

}
