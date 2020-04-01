// Copyright (c) 2018-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @since 4.8.0
 */
public class DefaultRetryHandler implements RetryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRetryHandler.class);

    private final RetryCondition<? super RecordedQueue> queueRecoveryRetryCondition;
    private final RetryCondition<? super RecordedExchange> exchangeRecoveryRetryCondition;
    private final RetryCondition<? super RecordedBinding> bindingRecoveryRetryCondition;
    private final RetryCondition<? super RecordedConsumer> consumerRecoveryRetryCondition;

    private final RetryOperation<?> queueRecoveryRetryOperation;
    private final RetryOperation<?> exchangeRecoveryRetryOperation;
    private final RetryOperation<?> bindingRecoveryRetryOperation;
    private final RetryOperation<?> consumerRecoveryRetryOperation;

    private final int retryAttempts;

    private final BackoffPolicy backoffPolicy;

    public DefaultRetryHandler(RetryCondition<? super RecordedQueue> queueRecoveryRetryCondition,
        RetryCondition<? super RecordedExchange> exchangeRecoveryRetryCondition,
        RetryCondition<? super RecordedBinding> bindingRecoveryRetryCondition,
        RetryCondition<? super RecordedConsumer> consumerRecoveryRetryCondition,
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

    protected <T extends RecordedEntity> RetryResult doRetry(RetryCondition<T> condition, RetryOperation<?> operation, T entity, RetryContext context)
        throws Exception {
        int attempts = 0;
        Exception exception = context.exception();
        while (attempts < retryAttempts) {
            if (condition.test(entity, exception)) {
                log(entity, exception, attempts);
                backoffPolicy.backoff(attempts + 1);
                try {
                    Object result = operation.call(context);
                    return new RetryResult(
                        entity, result == null ? null : result.toString()
                    );
                } catch (Exception e) {
                    exception = e;
                    attempts++;
                }
            } else {
                throw exception;
            }
        }
        throw exception;
    }

    protected void log(RecordedEntity entity, Exception exception, int attempts) {
        LOGGER.info("Error while recovering {}, retrying with {} more attempt(s).", entity, retryAttempts - attempts, exception);
    }

    public static abstract class RetryOperation<T> {

        public abstract T call(RetryContext context) throws Exception;

        public <V> RetryOperation<V> andThen(final RetryOperation<V> after) {
            return new RetryOperation<V>() {

                @Override
                public V call(RetryContext context) throws Exception {
                    RetryOperation.this.call(context);
                    return after.call(context);
                }
            };
        }
    }

    public static abstract class RetryCondition<E extends RecordedEntity> {

        public abstract boolean test(E entity, Exception ex);

        public RetryCondition<E> and(final RetryCondition<? super E> other) {
            if (other == null) {
                throw new IllegalArgumentException("Condition cannot be null");
            }
            return new RetryCondition<E>() {

                @Override
                public boolean test(E entity, Exception ex) {
                    return RetryCondition.this.test(entity, ex) && other.test(entity, ex);
                }
            };
        }

        public RetryCondition<E> negate(final RetryCondition<? super E> other) {
            if (other == null) {
                throw new IllegalArgumentException("Condition cannot be null");
            }
            return new RetryCondition<E>() {

                @Override
                public boolean test(E entity, Exception ex) {
                    return !RetryCondition.this.test(entity, ex);
                }
            };
        }
    }
}
