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

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Composable topology recovery retry handler.
 * This retry handler implementations let the user choose the condition
 * to trigger retry and the retry operation for each type of recoverable
 * entities. The number of attempts and the backoff policy (time to wait
 * between retries) are also configurable.
 * <p>
 * See also {@link TopologyRecoveryRetryHandlerBuilder} to easily create
 * instances and {@link TopologyRecoveryRetryLogic} for ready-to-use
 * conditions and operations.
 *
 * @see TopologyRecoveryRetryHandlerBuilder
 * @see TopologyRecoveryRetryLogic
 * @since 5.4.0
 */
public class DefaultRetryHandler implements RetryHandler {

    private final BiPredicate<RecordedQueue, Exception> queueRecoveryRetryCondition;
    private final BiPredicate<RecordedExchange, Exception> exchangeRecoveryRetryCondition;
    private final BiPredicate<RecordedBinding, Exception> bindingRecoveryRetryCondition;
    private final BiPredicate<RecordedConsumer, Exception> consumerRecoveryRetryCondition;

    private final RetryOperation<?> queueRecoveryRetryOperation;
    private final RetryOperation<?> exchangeRecoveryRetryOperation;
    private final RetryOperation<?> bindingRecoveryRetryOperation;
    private final RetryOperation<?> consumerRecoveryRetryOperation;

    private final int retryAttempts;

    private final BackoffPolicy backoffPolicy;

    public DefaultRetryHandler(BiPredicate<RecordedQueue, Exception> queueRecoveryRetryCondition,
        BiPredicate<RecordedExchange, Exception> exchangeRecoveryRetryCondition,
        BiPredicate<RecordedBinding, Exception> bindingRecoveryRetryCondition,
        BiPredicate<RecordedConsumer, Exception> consumerRecoveryRetryCondition,
        RetryOperation<?> queueRecoveryRetryOperation,
        RetryOperation<?> exchangeRecoveryRetryOperation,
        RetryOperation<?> bindingRecoveryRetryOperation,
        RetryOperation<?> consumerRecoveryRetryOperation, int retryAttempts, BackoffPolicy backoffPolicy) {
        this.queueRecoveryRetryCondition = queueRecoveryRetryCondition;
        this.exchangeRecoveryRetryCondition = exchangeRecoveryRetryCondition;
        this.bindingRecoveryRetryCondition = bindingRecoveryRetryCondition;
        this.consumerRecoveryRetryCondition = consumerRecoveryRetryCondition;
        this.queueRecoveryRetryOperation = queueRecoveryRetryOperation;
        this.exchangeRecoveryRetryOperation = exchangeRecoveryRetryOperation;
        this.bindingRecoveryRetryOperation = bindingRecoveryRetryOperation;
        this.consumerRecoveryRetryOperation = consumerRecoveryRetryOperation;
        this.backoffPolicy = backoffPolicy;
        if (retryAttempts <= 0) {
            throw new IllegalArgumentException("Number of retry attempts must be greater than 0");
        }
        this.retryAttempts = retryAttempts;
    }

    @Override
    public RetryResult retryQueueRecovery(RetryContext context) throws Exception {
        return doRetry(queueRecoveryRetryCondition, queueRecoveryRetryOperation, context.queue(), context);
    }

    @Override
    public RetryResult retryExchangeRecovery(RetryContext context) throws Exception {
        return doRetry(exchangeRecoveryRetryCondition, exchangeRecoveryRetryOperation, context.exchange(), context);
    }

    @Override
    public RetryResult retryBindingRecovery(RetryContext context) throws Exception {
        return doRetry(bindingRecoveryRetryCondition, bindingRecoveryRetryOperation, context.binding(), context);
    }

    @Override
    public RetryResult retryConsumerRecovery(RetryContext context) throws Exception {
        return doRetry(consumerRecoveryRetryCondition, consumerRecoveryRetryOperation, context.consumer(), context);
    }

    protected <T extends RecordedEntity> RetryResult doRetry(BiPredicate<T, Exception> condition, RetryOperation<?> operation, T entity, RetryContext context)
        throws Exception {
        int attempts = 0;
        Exception exception = context.exception();
        while (attempts < retryAttempts) {
            if (condition.test(entity, exception)) {
                backoffPolicy.backoff(attempts + 1);
                try {
                    Object result = operation.call(context);
                    return new RetryResult(
                        entity, result == null ? null : result.toString()
                    );
                } catch (Exception e) {
                    exception = e;
                    attempts++;
                    continue;
                }
            } else {
                throw exception;
            }
        }
        throw context.exception();
    }

    public interface RetryOperation<T> {

        T call(RetryContext context) throws Exception;

        default <V> RetryOperation<V> andThen(RetryOperation<V> after) {
            Objects.requireNonNull(after);
            return (context) -> {
                call(context);
                return after.call(context);
            };
        }
    }
}
