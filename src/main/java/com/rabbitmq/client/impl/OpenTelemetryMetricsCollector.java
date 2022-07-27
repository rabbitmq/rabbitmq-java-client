// Copyright (c) 2022 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MetricsCollector;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * <a href="https://opentelemetry.io/">OpenTelemetry</a> implementation of {@link MetricsCollector}.
 *
 * @see MetricsCollector
 * @since 5.16.0
 */
public class OpenTelemetryMetricsCollector extends AbstractMetricsCollector {

    private final Attributes attributes;

    private final AtomicLong connections = new AtomicLong(0L);
    private final AtomicLong channels = new AtomicLong(0L);

    private final LongCounter publishedMessagesCounter;
    private final LongCounter consumedMessagesCounter;
    private final LongCounter acknowledgedMessagesCounter;
    private final LongCounter rejectedMessagesCounter;
    private final LongCounter failedToPublishMessagesCounter;
    private final LongCounter ackedPublishedMessagesCounter;
    private final LongCounter nackedPublishedMessagesCounter;
    private final LongCounter unroutedPublishedMessagesCounter;

    public OpenTelemetryMetricsCollector(OpenTelemetry openTelemetry) {
        this(openTelemetry, "rabbitmq");
    }

    public OpenTelemetryMetricsCollector(final OpenTelemetry openTelemetry, final String prefix) {
        this(openTelemetry, prefix, Attributes.empty());
    }

    public OpenTelemetryMetricsCollector(final OpenTelemetry openTelemetry, final String prefix, final Attributes attributes) {
        // initialize meter
        Meter meter = openTelemetry.getMeter("amqp-client");

        // attributes
        this.attributes = attributes;

        // connections
        meter.gaugeBuilder(prefix + ".connections")
            .setUnit("{connections}")
            .setDescription("The number of connections to the RabbitMQ server")
            .ofLongs()
            .buildWithCallback(measurement -> measurement.record(connections.get(), attributes));

        // channels
        meter.gaugeBuilder(prefix + ".channels")
            .setUnit("{channels}")
            .setDescription("The number of channels to the RabbitMQ server")
            .ofLongs()
            .buildWithCallback(measurement -> measurement.record(channels.get(), attributes));

        // publishedMessages
        this.publishedMessagesCounter = meter.counterBuilder(prefix + ".published")
            .setUnit("{messages}")
            .setDescription("The number of messages published to the RabbitMQ server")
            .build();

        // consumedMessages
        this.consumedMessagesCounter = meter.counterBuilder(prefix + ".consumed")
            .setUnit("{messages}")
            .setDescription("The number of messages consumed from the RabbitMQ server")
            .build();

        // acknowledgedMessages
        this.acknowledgedMessagesCounter = meter.counterBuilder(prefix + ".acknowledged")
            .setUnit("{messages}")
            .setDescription("The number of messages acknowledged from the RabbitMQ server")
            .build();

        // rejectedMessages
        this.rejectedMessagesCounter = meter.counterBuilder(prefix + ".rejected")
            .setUnit("{messages}")
            .setDescription("The number of messages rejected from the RabbitMQ server")
            .build();

        // failedToPublishMessages
        this.failedToPublishMessagesCounter = meter.counterBuilder(prefix + ".failed_to_publish")
            .setUnit("{messages}")
            .setDescription("The number of messages failed to publish to the RabbitMQ server")
            .build();

        // ackedPublishedMessages
        this.ackedPublishedMessagesCounter = meter.counterBuilder(prefix + ".acknowledged_published")
            .setUnit("{messages}")
            .setDescription("The number of published messages acknowledged by the RabbitMQ server")
            .build();

        // nackedPublishedMessages
        this.nackedPublishedMessagesCounter = meter.counterBuilder(prefix + ".not_acknowledged_published")
            .setUnit("{messages}")
            .setDescription("The number of published messages not acknowledged by the RabbitMQ server")
            .build();

        // unroutedPublishedMessages
        this.unroutedPublishedMessagesCounter = meter.counterBuilder(prefix + ".unrouted_published")
            .setUnit("{messages}")
            .setDescription("The number of un-routed published messages to the RabbitMQ server")
            .build();
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
        publishedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markMessagePublishFailed() {
        failedToPublishMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markConsumedMessage() {
        consumedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markAcknowledgedMessage() {
        acknowledgedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markRejectedMessage() {
        rejectedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markMessagePublishAcknowledged() {
        ackedPublishedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markMessagePublishNotAcknowledged() {
        nackedPublishedMessagesCounter.add(1L, attributes);
    }

    @Override
    protected void markPublishedMessageUnrouted() {
        unroutedPublishedMessagesCounter.add(1L, attributes);
    }

    public AtomicLong getConnections() {
        return connections;
    }

    public AtomicLong getChannels() {
        return channels;
    }
}
