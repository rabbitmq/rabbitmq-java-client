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

/**
 * Useful ready-to-use conditions and operations for {@link DefaultRetryHandler}.
 * They're composed and used with the {@link TopologyRecoveryRetryHandlerBuilder}.
 *
 * @see DefaultRetryHandler
 * @see RetryHandler
 * @see TopologyRecoveryRetryHandlerBuilder
 * @since 4.8.0
 */
public abstract class TopologyRecoveryRetryLogic {

    public static final DefaultRetryHandler.RetryCondition<RecordedEntity> CHANNEL_CLOSED_NOT_FOUND = new DefaultRetryHandler.RetryCondition<RecordedEntity>() {

        @Override
        public boolean test(RecordedEntity entity, Exception e) {
            if (e.getCause() instanceof ShutdownSignalException) {
                ShutdownSignalException cause = (ShutdownSignalException) e.getCause();
                if (cause.getReason() instanceof AMQP.Channel.Close) {
                    return ((AMQP.Channel.Close) cause.getReason()).getReplyCode() == 404;
                }
            }
            return false;
        }
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_CHANNEL = new DefaultRetryHandler.RetryOperation<Void>() {

        @Override
        public Void call(RetryContext context) throws Exception {
            if (!context.entity().getChannel().isOpen()) {
                context.connection().recoverChannel(context.entity().getChannel());
            }
            return null;
        }
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_BINDING_QUEUE = new DefaultRetryHandler.RetryOperation<Void>() {

        @Override
        public Void call(RetryContext context) {
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
        }
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_BINDING = new DefaultRetryHandler.RetryOperation<Void>() {

        @Override
        public Void call(RetryContext context) throws Exception {
            context.binding().recover();
            return null;
        }
    };

    public static final DefaultRetryHandler.RetryOperation<Void> RECOVER_CONSUMER_QUEUE = new DefaultRetryHandler.RetryOperation<Void>() {

        @Override
        public Void call(RetryContext context) {
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
        }
    };

    public static final DefaultRetryHandler.RetryOperation<String> RECOVER_CONSUMER = new DefaultRetryHandler.RetryOperation<String>() {

        @Override
        public String call(RetryContext context) throws Exception {
            return context.consumer().recover();
        }
    };
}
