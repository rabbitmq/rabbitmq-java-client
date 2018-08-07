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
 * Contract to retry failed operations during topology recovery.
 * Not all operations have to be retried, it's a decision of the
 * underlying implementation.
 *
 * @since 5.4.0
 */
public interface RetryHandler {

    /**
     * Retry a failed queue recovery operation.
     *
     * @param context the context of the retry
     * @return the result of the retry attempt
     * @throws Exception if the retry fails
     */
    RetryResult retryQueueRecovery(RetryContext context) throws Exception;

    /**
     * Retry a failed exchange recovery operation.
     *
     * @param context the context of the retry
     * @return the result of the retry attempt
     * @throws Exception if the retry fails
     */
    RetryResult retryExchangeRecovery(RetryContext context) throws Exception;

    /**
     * Retry a failed binding recovery operation.
     *
     * @param context the context of the retry
     * @return the result of the retry attempt
     * @throws Exception if the retry fails
     */
    RetryResult retryBindingRecovery(RetryContext context) throws Exception;

    /**
     * Retry a failed consumer recovery operation.
     *
     * @param context the context of the retry
     * @return the result of the retry attempt
     * @throws Exception if the retry fails
     */
    RetryResult retryConsumerRecovery(RetryContext context) throws Exception;
}
