// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandler;
import com.rabbitmq.client.observation.ObservationCollector;

import java.util.concurrent.ThreadFactory;

/**
 * {@link com.rabbitmq.client.impl.AMQConnection} modification that uses {@link com.rabbitmq.client.impl.recovery.RecoveryAwareChannelN}
 * @since 3.3.0
 */
public class RecoveryAwareAMQConnection extends AMQConnection {

    public RecoveryAwareAMQConnection(ConnectionParams params, FrameHandler handler,
                                      MetricsCollector metricsCollector, ObservationCollector observationCollector) {
        super(params, handler, metricsCollector, observationCollector);
    }

    public RecoveryAwareAMQConnection(ConnectionParams params, FrameHandler handler) {
        super(params, handler);
    }

    @Override
    protected RecoveryAwareChannelManager instantiateChannelManager(int channelMax, ThreadFactory threadFactory) {
        RecoveryAwareChannelManager recoveryAwareChannelManager = new RecoveryAwareChannelManager(
            super._workService, channelMax, threadFactory,
            this.metricsCollector, this.observationCollector);
        configureChannelManager(recoveryAwareChannelManager);
        return recoveryAwareChannelManager;
    }
}
