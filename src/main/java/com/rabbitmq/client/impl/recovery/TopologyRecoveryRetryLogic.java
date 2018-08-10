// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Useful ready-to-use conditions and operations for {@link DefaultRetryHandler}.
 * They're composed and used with the {@link TopologyRecoveryRetryHandlerBuilder}.
 *
 * @see DefaultRetryHandler
 * @see RetryHandler
 * @see TopologyRecoveryRetryHandlerBuilder
 * @since 5.4.0
 */
public abstract class TopologyRecoveryRetryLogic {

    public static final BiPredicate<RecordedEntity, Exception> CHANNEL_CLOSED_NOT_FOUND = (entity, ex) -> {
        if (ex.getCause() instanceof ShutdownSignalException) {
            ShutdownSignalException cause = (ShutdownSignalException) ex.getCause();
            if (cause.getReason() instanceof AMQP.Channel.Close) {
                return ((AMQP.Channel.Close) cause.getReason()).getReplyCode() == 404;
            }
        }
        return false;
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_CHANNEL = context -> {
        if (!context.entity().getChannel().isOpen()) {
            context.connection().recoverChannel(context.entity().getChannel());
        }
        return null;
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_BINDING_QUEUE = context -> {
        if (context.entity() instanceof RecordedQueueBinding) {
            RecordedBinding binding = context.binding();
            AutorecoveringConnection connection = context.connection();
            RecordedQueue recordedQueue = connection.getRecordedQueues().get(binding.getDestination());
            if (recordedQueue != null) {
                connection.recoverQueue(
                    recordedQueue.getName(), recordedQueue, false
                );
            }
        }
        return null;
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_BINDING = context -> {
        context.binding().recover();
        return null;
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_CONSUMER_QUEUE = context -> {
        if (context.entity() instanceof RecordedConsumer) {
            RecordedConsumer consumer = context.consumer();
            AutorecoveringConnection connection = context.connection();
            RecordedQueue recordedQueue = connection.getRecordedQueues().get(consumer.getQueue());
            if (recordedQueue != null) {
                connection.recoverQueue(
                    recordedQueue.getName(), recordedQueue, false
                );
            }
        }
        return null;
    };

    public static final DefaultRetryHandler.RetryOperation<String> RECOVER_CONSUMER = context -> context.consumer().recover();
}
