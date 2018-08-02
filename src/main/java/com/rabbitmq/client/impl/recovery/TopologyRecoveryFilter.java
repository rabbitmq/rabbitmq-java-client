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

/**
 * Filter to know whether entities should be recovered or not.
 * @since 4.8.0
 */
public interface TopologyRecoveryFilter {

    /**
     * Decides whether an exchange is recovered or not.
     * @param recordedExchange
     * @return true to recover the exchange, false otherwise
     */
    default boolean filterExchange(RecordedExchange recordedExchange) {
        return true;
    }

    /**
     * Decides whether a queue is recovered or not.
     * @param recordedQueue
     * @return true to recover the queue, false otherwise
     */
    default boolean filterQueue(RecordedQueue recordedQueue) {
        return true;
    }

    /**
     * Decides whether a binding is recovered or not.
     * @param recordedBinding
     * @return true to recover the binding, false otherwise
     */
    default boolean filterBinding(RecordedBinding recordedBinding) {
        return true;
    }

    /**
     * Decides whether a consumer is recovered or not.
     * @param recordedConsumer
     * @return true to recover the consumer, false otherwise
     */
    default boolean filterConsumer(RecordedConsumer recordedConsumer) {
        return true;
    }

}
