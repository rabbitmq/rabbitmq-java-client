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

/**
 * Builder to ease creation of {@link DefaultRetryHandler} instances.
 * <p>
 * Just override what you need. By default, retry conditions don't trigger retry,
 * retry operations are no-op, the number of retry attempts is 1, and the backoff
 * policy doesn't wait at all.
 *
 * @see DefaultRetryHandler
 * @see TopologyRecoveryRetryLogic
 * @since 4.8.0
 */
public class TopologyRecoveryRetryHandlerBuilder {

    private DefaultRetryHandler.RetryCondition<? super RecordedQueue> queueRecoveryRetryCondition = new DefaultRetryHandler.RetryCondition<RecordedQueue>() {

        @Override
        public boolean test(RecordedQueue entity, Exception ex) {
            return false;
        }
    };
    private DefaultRetryHandler.RetryCondition<? super RecordedExchange> exchangeRecoveryRetryCondition = new DefaultRetryHandler.RetryCondition<RecordedExchange>() {

        @Override
        public boolean test(RecordedExchange entity, Exception ex) {
            return false;
        }
    };
    private DefaultRetryHandler.RetryCondition<? super RecordedBinding> bindingRecoveryRetryCondition = new DefaultRetryHandler.RetryCondition<RecordedBinding>() {

        @Override
        public boolean test(RecordedBinding entity, Exception ex) {
            return false;
        }
    };
    private DefaultRetryHandler.RetryCondition<? super RecordedConsumer> consumerRecoveryRetryCondition = new DefaultRetryHandler.RetryCondition<RecordedConsumer>() {

        @Override
        public boolean test(RecordedConsumer entity, Exception ex) {
            return false;
        }
    };

    private DefaultRetryHandler.RetryOperation<?> queueRecoveryRetryOperation = new DefaultRetryHandler.RetryOperation<Object>() {

        @Override
        public Object call(RetryContext context) {
            return null;
        }
    };
    private DefaultRetryHandler.RetryOperation<?> exchangeRecoveryRetryOperation = new DefaultRetryHandler.RetryOperation<Object>() {

        @Override
        public Object call(RetryContext context) {
            return null;
        }
    };
    private DefaultRetryHandler.RetryOperation<?> bindingRecoveryRetryOperation = new DefaultRetryHandler.RetryOperation<Object>() {

        @Override
        public Object call(RetryContext context) {
            return null;
        }
    };
    private DefaultRetryHandler.RetryOperation<?> consumerRecoveryRetryOperation = new DefaultRetryHandler.RetryOperation<Object>() {

        @Override
        public Object call(RetryContext context) {
            return null;
        }
    };

    private int retryAttempts = 2;

    private BackoffPolicy backoffPolicy = new BackoffPolicy() {

        @Override
        public void backoff(int attemptNumber) {

        }
    };

    public static TopologyRecoveryRetryHandlerBuilder builder() {
        return new TopologyRecoveryRetryHandlerBuilder();
    }

    public TopologyRecoveryRetryHandlerBuilder queueRecoveryRetryCondition(
        DefaultRetryHandler.RetryCondition<? super RecordedQueue> queueRecoveryRetryCondition) {
        this.queueRecoveryRetryCondition = queueRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder exchangeRecoveryRetryCondition(
        DefaultRetryHandler.RetryCondition<? super RecordedExchange> exchangeRecoveryRetryCondition) {
        this.exchangeRecoveryRetryCondition = exchangeRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder bindingRecoveryRetryCondition(
        DefaultRetryHandler.RetryCondition<? super RecordedBinding> bindingRecoveryRetryCondition) {
        this.bindingRecoveryRetryCondition = bindingRecoveryRetryCondition;
        return this;
    }

    public TopologyRecoveryRetryHandlerBuilder consumerRecoveryRetryCondition(
        DefaultRetryHandler.RetryCondition<? super RecordedConsumer> consumerRecoveryRetryCondition) {
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
