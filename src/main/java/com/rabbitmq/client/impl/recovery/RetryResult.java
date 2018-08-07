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
 * The retry of a retried topology recovery operation.
 *
 * @since 5.4.0
 */
public class RetryResult {

    /**
     * The entity to recover.
     */
    private final RecordedEntity recordedEntity;

    /**
     * The result of the recovery operation.
     * E.g. a consumer tag when recovering a consumer.
     */
    private final Object result;

    public RetryResult(RecordedEntity recordedEntity, Object result) {
        this.recordedEntity = recordedEntity;
        this.result = result;
    }

    /**
     * The entity to recover.
     *
     * @return
     */
    public RecordedEntity getRecordedEntity() {
        return recordedEntity;
    }

    /**
     * The result of the recovery operation.
     * E.g. a consumer tag when recovering a consumer.
     */
    public Object getResult() {
        return result;
    }
}
