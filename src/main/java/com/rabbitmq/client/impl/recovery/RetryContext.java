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
 * The context of a topology recovery retry operation.
 *
 * @since 5.4.0
 */
public class RetryContext {

    private final RecordedEntity entity;

    private final Exception exception;

    private final AutorecoveringConnection connection;

    public RetryContext(RecordedEntity entity, Exception exception, AutorecoveringConnection connection) {
        this.entity = entity;
        this.exception = exception;
        this.connection = connection;
    }

    /**
     * The underlying connection.
     *
     * @return
     */
    public AutorecoveringConnection connection() {
        return connection;
    }

    /**
     * The exception that triggered the retry attempt.
     *
     * @return
     */
    public Exception exception() {
        return exception;
    }

    /**
     * The to-be-recovered entity.
     *
     * @return
     */
    public RecordedEntity entity() {
        return entity;
    }

    /**
     * The to-be-recovered entity as a queue.
     *
     * @return
     */
    public RecordedQueue queue() {
        return (RecordedQueue) entity;
    }

    /**
     * The to-be-recovered entity as an exchange.
     *
     * @return
     */
    public RecordedExchange exchange() {
        return (RecordedExchange) entity;
    }

    /**
     * The to-be-recovered entity as a binding.
     *
     * @return
     */
    public RecordedBinding binding() {
        return (RecordedBinding) entity;
    }

    /**
     * The to-be-recovered entity as a consumer.
     *
     * @return
     */
    public RecordedConsumer consumer() {
        return (RecordedConsumer) entity;
    }
}
