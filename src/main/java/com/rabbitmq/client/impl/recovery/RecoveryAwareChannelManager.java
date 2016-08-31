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

package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.NoOpMetricsCollector;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelManager;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @since 3.3.0
 */
public class RecoveryAwareChannelManager extends ChannelManager {
    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax) {
        this(workService, channelMax, Executors.defaultThreadFactory());
    }

    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory) {
        super(workService, channelMax, threadFactory, new NoOpMetricsCollector());
    }

    public RecoveryAwareChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory, MetricsCollector metricsCollector) {
        super(workService, channelMax, threadFactory, metricsCollector);
    }

    @Override
    protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new RecoveryAwareChannelN(connection, channelNumber, workService, this.metricsCollector);
    }
}
