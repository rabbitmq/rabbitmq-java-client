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

package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.Command;
import com.rabbitmq.client.NoOpMetricsCollector;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import com.rabbitmq.client.impl.AMQImpl.Basic;

import java.io.IOException;

/**
 * {@link com.rabbitmq.client.impl.ChannelN} modification that keeps track of delivery
 * tags and avoids sending <pre>basic.ack</pre>, <pre>basic.nack</pre>, and <pre>basic.reject</pre>
 * for stale tags.
 *
 * @since 3.3.0
 */
public class RecoveryAwareChannelN extends ChannelN {
    private volatile long maxSeenDeliveryTag = 0;
    private volatile long activeDeliveryTagOffset = 0;

    /**
     * Construct a new channel on the given connection with the given
     * channel number. Usually not called directly - call
     * Connection.createChannel instead.
     *
     * @param connection    The connection associated with this channel
     * @param channelNumber The channel number to be associated with this channel
     * @param workService   service for managing this channel's consumer callbacks
     */
    public RecoveryAwareChannelN(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        this(connection, channelNumber, workService, new NoOpMetricsCollector());
    }

    /**
     * Construct a new channel on the given connection with the given
     * channel number. Usually not called directly - call
     * Connection.createChannel instead.
     *
     * @param connection    The connection associated with this channel
     * @param channelNumber The channel number to be associated with this channel
     * @param workService   service for managing this channel's consumer callbacks
     * @param metricsCollector service for managing metrics
     */
    public RecoveryAwareChannelN(AMQConnection connection, int channelNumber, ConsumerWorkService workService, MetricsCollector metricsCollector) {
        super(connection, channelNumber, workService, metricsCollector);
    }

    @Override
    protected void processDelivery(Command command, AMQImpl.Basic.Deliver method) {
        long tag = method.getDeliveryTag();
        if(tag > maxSeenDeliveryTag) {
            maxSeenDeliveryTag = tag;
        }
        super.processDelivery(command, offsetDeliveryTag(method));
    }

    private AMQImpl.Basic.Deliver offsetDeliveryTag(AMQImpl.Basic.Deliver method) {
        return new AMQImpl.Basic.Deliver(method.getConsumerTag(),
                                         method.getDeliveryTag() + activeDeliveryTagOffset,
                                         method.getRedelivered(),
                                         method.getExchange(),
                                         method.getRoutingKey());
    }

    @Override
    public void basicAck(long deliveryTag, boolean multiple) throws IOException {
        long realTag = deliveryTag - activeDeliveryTagOffset;
        if(multiple && deliveryTag == 0) {
            // 0 tag means ack all when multiple is set
            realTag = 0;
        } else if(realTag <= 0) {
            return;
        }
        transmit(new Basic.Ack(realTag, multiple));
        metricsCollector.basicAck(this, deliveryTag, multiple);
    }

    @Override
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        long realTag = deliveryTag - activeDeliveryTagOffset;
        if(multiple && deliveryTag == 0) {
            // 0 tag means nack all when multiple is set
            realTag = 0;
        } else if(realTag <= 0) {
            return;
        }
        transmit(new Basic.Nack(realTag, multiple, requeue));
        metricsCollector.basicNack(this, deliveryTag);
    }

    @Override
    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        long realTag = deliveryTag - activeDeliveryTagOffset;
        if (realTag > 0) {
            transmit(new Basic.Reject(realTag, requeue));
            metricsCollector.basicReject(this, deliveryTag);
        }
    }

    void inheritOffsetFrom(RecoveryAwareChannelN other) {
        activeDeliveryTagOffset = other.getActiveDeliveryTagOffset() + other.getMaxSeenDeliveryTag();
        maxSeenDeliveryTag = 0;
    }

    public long getMaxSeenDeliveryTag() {
        return maxSeenDeliveryTag;
    }

    public long getActiveDeliveryTagOffset() {
        return activeDeliveryTagOffset;
    }
}
