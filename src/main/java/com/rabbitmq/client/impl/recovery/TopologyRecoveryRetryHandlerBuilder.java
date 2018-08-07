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

import java.util.function.BiPredicate;

/**
 * Builder to ease creation of {@link DefaultRetryHandler} instances.
 * <p>
 * Just override what you need. By default, retry conditions don't trigger retry,
 * retry operations are no-op, the number of retry attempts is 1, and the backoff
 * policy doesn't wait at all.
 *
 * @see DefaultRetryHandler
 * @see TopologyRecoveryRetryLogic
 * @since 5.4.0
 */
public class TopologyRecoveryRetryHandlerBuilder {

    private BiPredicate<RecordedQueue, Exception> queueRecoveryRetryCondition = (q, e) -> false;
    private BiPredicate<RecordedExchange, Exception> exchangeRecoveryRetryCondition = (ex, e) -> false;
    private BiPredicate<RecordedBinding, Exception> bindingRecoveryRetryCondition = (b, e) -> false;
    private BiPredicate<RecordedConsumer, Exception> consumerRecoveryRetryCondition = (c, e) -> false;

    private DefaultRetryHandler.RetryOperation<?> queueRecoveryRetryOperation = context -> null;
    private DefaultRetryHandler.RetryOperation<?> exchangeRecoveryRetryOperation = context -> null;
    private DefaultRetryHandler.RetryOperation<?> bindingRecoveryRetryOperation = context -> null;
    private DefaultRetryHandler.RetryOperation<?> consumerRecoveryRetryOperation = context -> null;

    private int retryAttempts = 1;

    private BackoffPolicy backoffPolicy = nbAttempts -> {
    };

    public static TopologyRecoveryRetryHandlerBuilder builder() {
        return new TopologyRecoveryRetryHandlerBuilder();
    }

    public TopologyRecoveryRetryHandlerBuilder queueRecoveryRetryCondition(
        BiPredicate<RecordedQueue, Exception> queueRecoveryRetryCondition) {
        this.queueRecoveryRetryCondition = queueRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder exchangeRecoveryRetryCondition(
        BiPredicate<RecordedExchange, Exception> exchangeRecoveryRetryCondition) {
        this.exchangeRecoveryRetryCondition = exchangeRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder bindingRecoveryRetryCondition(
        BiPredicate<RecordedBinding, Exception> bindingRecoveryRetryCondition) {
        this.bindingRecoveryRetryCondition = bindingRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder consumerRecoveryRetryCondition(
        BiPredicate<RecordedConsumer, Exception> consumerRecoveryRetryCondition) {
        this.consumerRecoveryRetryCondition = consumerRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder queueRecoveryRetryOperation(DefaultRetryHandler.RetryOperation<?> queueRecoveryRetryOperation) {
        this.queueRecoveryRetryOperation = queueRecoveryRetryOperation;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder exchangeRecoveryRetryOperation(DefaultRetryHandler.RetryOperation<?> exchangeRecoveryRetryOperation) {
        this.exchangeRecoveryRetryOperation = exchangeRecoveryRetryOperation;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder bindingRecoveryRetryOperation(DefaultRetryHandler.RetryOperation<?> bindingRecoveryRetryOperation) {
        this.bindingRecoveryRetryOperation = bindingRecoveryRetryOperation;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder consumerRecoveryRetryOperation(DefaultRetryHandler.RetryOperation<?> consumerRecoveryRetryOperation) {
        this.consumerRecoveryRetryOperation = consumerRecoveryRetryOperation;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder backoffPolicy(BackoffPolicy backoffPolicy) {
        this.backoffPolicy = backoffPolicy;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder retryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
        return this;
    }

    public RetryHandler build() {
        return new DefaultRetryHandler(
            queueRecoveryRetryCondition, exchangeRecoveryRetryCondition,
            bindingRecoveryRetryCondition, consumerRecoveryRetryCondition,
            queueRecoveryRetryOperation, exchangeRecoveryRetryOperation,
            bindingRecoveryRetryOperation, consumerRecoveryRetryOperation,
            retryAttempts,
            backoffPolicy);
    }
}
