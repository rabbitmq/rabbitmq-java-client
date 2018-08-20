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
 * Consider a long running task a consumer has to perform. Say, it takes 15 minutes to complete. In the
 * 15 minute window there is a reasonable chance of connection failure and recovery events. All delivery tags
 * for the deliveries being processed won't be valid after recovery because they are "reset" for
 * newly opened channels. This channel implementation will avoid sending out acknowledgements for such
 * stale delivery tags and avoid a guaranteed channel-level exception (and thus channel closure).
 *
 * This is a sufficient solution in practice because all unacknowledged deliveries will be requeued
 * by RabbitMQ automatically when it detects client connection loss.
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
        // Last delivery is likely the same one a long running consumer is still processing,
        // so realTag might end up being 0.
        //  has a special meaning in the protocol ("acknowledge all unacknowledged tags),
        // so if the user explicitly asks for that with multiple = true, do it.
        if(multiple && deliveryTag == 0) {
            // 0 tag means ack all when multiple is set
            realTag = 0;
        } else if(realTag <= 0) {
            // delivery tags start at 1, so the real tag is stale
            // therefore we should do nothing
            return;
        }
        transmit(new Basic.Ack(realTag, multiple));
        metricsCollector.basicAck(this, deliveryTag, multiple);
    }

    @Override
    public void basicNack(long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        // See the comment in basicAck above.
        long realTag = deliveryTag - activeDeliveryTagOffset;
        if(multiple && deliveryTag == 0) {
            // 0 tag means nack all when multiple is set
            realTag = 0;
        } else if(realTag <= 0) {
            // delivery tags start at 1, so the real tag is stale
            // therefore we should do nothing
            return;
        }
        transmit(new Basic.Nack(realTag, multiple, requeue));
        metricsCollector.basicNack(this, deliveryTag);
    }

    @Override
    public void basicReject(long deliveryTag, boolean requeue) throws IOException {
        // note that the basicAck comment above does not apply
        // here since basic.reject doesn't support rejecting
        // multiple deliveries at once
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
