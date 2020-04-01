// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MetricsCollector;

/**
 * Dropwizard Metrics implementation of {@link MetricsCollector}.
 * Note transactions are not supported (see {@link MetricsCollector}.
 * Metrics provides out-of-the-box support for report backends like JMX,
 * Graphite, Ganglia, or plain HTTP. See Metrics documentation for
 * more details.
 *
 * @see MetricsCollector
 */
public class StandardMetricsCollector extends AbstractMetricsCollector {

    private final MetricRegistry registry;

    private final Counter connections;
    private final Counter channels;
    private final Meter publishedMessages;
    private final Meter consumedMessages;
    private final Meter acknowledgedMessages;
    private final Meter rejectedMessages;


    public StandardMetricsCollector(MetricRegistry registry, String metricsPrefix) {
        this.registry = registry;
        this.connections = registry.counter(metricsPrefix+".connections");
        this.channels = registry.counter(metricsPrefix+".channels");
        this.publishedMessages = registry.meter(metricsPrefix+".published");
        this.consumedMessages = registry.meter(metricsPrefix+".consumed");
        this.acknowledgedMessages = registry.meter(metricsPrefix+".acknowledged");
        this.rejectedMessages = registry.meter(metricsPrefix+".rejected");
    }

    public StandardMetricsCollector() {
        this(new MetricRegistry());
    }
    
    public StandardMetricsCollector(MetricRegistry metricRegistry) {
        this(metricRegistry, "rabbitmq");
    }

    @Override
    protected void incrementConnectionCount(Connection connection) {
        connections.inc();
    }

    @Override
    protected void decrementConnectionCount(Connection connection) {
        connections.dec();
    }

    @Override
    protected void incrementChannelCount(Channel channel) {
        channels.inc();
    }

    @Override
    protected void decrementChannelCount(Channel channel) {
        channels.dec();
    }

    @Override
    protected void markPublishedMessage() {
        publishedMessages.mark();
    }

    @Override
    protected void markConsumedMessage() {
        consumedMessages.mark();
    }

    @Override
    protected void markAcknowledgedMessage() {
        acknowledgedMessages.mark();
    }

    @Override
    protected void markRejectedMessage() {
        rejectedMessages.mark();
    }


    
    public MetricRegistry getMetricRegistry() {
        return registry;
    }

    public Counter getConnections() {
        return connections;
    }

    public Counter getChannels() {
        return channels;
    }

    public Meter getPublishedMessages() {
        return publishedMessages;
    }

    public Meter getConsumedMessages() {
        return consumedMessages;
    }

    public Meter getAcknowledgedMessages() {
        return acknowledgedMessages;
    }

    public Meter getRejectedMessages() {
        return rejectedMessages;
    }
}
