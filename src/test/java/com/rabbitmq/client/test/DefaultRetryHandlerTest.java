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

package com.rabbitmq.client.test;

import com.rabbitmq.client.impl.recovery.BackoffPolicy;
import com.rabbitmq.client.impl.recovery.DefaultRetryHandler;
import com.rabbitmq.client.impl.recovery.RecordedBinding;
import com.rabbitmq.client.impl.recovery.RecordedConsumer;
import com.rabbitmq.client.impl.recovery.RecordedExchange;
import com.rabbitmq.client.impl.recovery.RecordedQueue;
import com.rabbitmq.client.impl.recovery.RetryContext;
import com.rabbitmq.client.impl.recovery.RetryHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 *
 */
public class DefaultRetryHandlerTest {

    RetryHandler handler;

    @Mock
    BiPredicate<RecordedQueue, Exception> queueRecoveryRetryCondition;
    @Mock
    BiPredicate<RecordedExchange, Exception> exchangeRecoveryRetryCondition;
    @Mock
    BiPredicate<RecordedBinding, Exception> bindingRecoveryRetryCondition;
    @Mock
    BiPredicate<RecordedConsumer, Exception> consumerRecoveryRetryCondition;

    @Mock
    DefaultRetryHandler.RetryOperation queueRecoveryRetryOperation;
    @Mock
    DefaultRetryHandler.RetryOperation exchangeRecoveryRetryOperation;
    @Mock
    DefaultRetryHandler.RetryOperation bindingRecoveryRetryOperation;
    @Mock
    DefaultRetryHandler.RetryOperation consumerRecoveryRetryOperation;

    @Mock
    BackoffPolicy backoffPolicy;

    @Before
    public void init() {
        initMocks(this);
    }

    @Test
    public void shouldNotRetryWhenConditionReturnsFalse() throws Exception {
        conditionsReturn(false);
        handler = handler();
        assertExceptionIsThrown(
            "No retry, initial exception should have been re-thrown",
            () -> handler.retryQueueRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "No retry, initial exception should have been re-thrown",
            () -> handler.retryExchangeRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "No retry, initial exception should have been re-thrown",
            () -> handler.retryBindingRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "No retry, initial exception should have been re-thrown",
            () -> handler.retryConsumerRecovery(retryContext())
        );
        verifyConditionsInvocation(times(1));
        verifyOperationsInvocation(never());
        verify(backoffPolicy, never()).backoff(anyInt());
    }

    @Test
    public void shouldReturnOperationResultInRetryResultWhenRetrying() throws Exception {
        conditionsReturn(true);
        when(queueRecoveryRetryOperation.call(any(RetryContext.class))).thenReturn("queue");
        when(exchangeRecoveryRetryOperation.call(any(RetryContext.class))).thenReturn("exchange");
        when(bindingRecoveryRetryOperation.call(any(RetryContext.class))).thenReturn("binding");
        when(consumerRecoveryRetryOperation.call(any(RetryContext.class))).thenReturn("consumer");
        handler = handler();
        assertEquals(
            "queue",
            handler.retryQueueRecovery(retryContext()).getResult()
        );
        assertEquals(
            "exchange",
            handler.retryExchangeRecovery(retryContext()).getResult()
        );
        assertEquals(
            "binding",
            handler.retryBindingRecovery(retryContext()).getResult()
        );
        assertEquals(
            "consumer",
            handler.retryConsumerRecovery(retryContext()).getResult()
        );
        verifyConditionsInvocation(times(1));
        verifyOperationsInvocation(times(1));
        verify(backoffPolicy, times(1 * 4)).backoff(1);
    }

    @Test
    public void shouldRetryWhenOperationFailsAndConditionIsTrue() throws Exception {
        conditionsReturn(true);
        when(queueRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception()).thenReturn("queue");
        when(exchangeRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception()).thenReturn("exchange");
        when(bindingRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception()).thenReturn("binding");
        when(consumerRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception()).thenReturn("consumer");
        handler = handler(2);
        assertEquals(
            "queue",
            handler.retryQueueRecovery(retryContext()).getResult()
        );
        assertEquals(
            "exchange",
            handler.retryExchangeRecovery(retryContext()).getResult()
        );
        assertEquals(
            "binding",
            handler.retryBindingRecovery(retryContext()).getResult()
        );
        assertEquals(
            "consumer",
            handler.retryConsumerRecovery(retryContext()).getResult()
        );
        verifyConditionsInvocation(times(2));
        verifyOperationsInvocation(times(2));
        checkBackoffSequence(1, 2, 1, 2, 1, 2, 1, 2);
    }

    @Test
    public void shouldThrowExceptionWhenRetryAttemptsIsExceeded() throws Exception {
        conditionsReturn(true);
        when(queueRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception());
        when(exchangeRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception());
        when(bindingRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception());
        when(consumerRecoveryRetryOperation.call(any(RetryContext.class)))
            .thenThrow(new Exception());
        handler = handler(3);
        assertExceptionIsThrown(
            "Retry exhausted, an exception should have been thrown",
            () -> handler.retryQueueRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "Retry exhausted, an exception should have been thrown",
            () -> handler.retryExchangeRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "Retry exhausted, an exception should have been thrown",
            () -> handler.retryBindingRecovery(retryContext())
        );
        assertExceptionIsThrown(
            "Retry exhausted, an exception should have been thrown",
            () -> handler.retryConsumerRecovery(retryContext())
        );
        verifyConditionsInvocation(times(3));
        verifyOperationsInvocation(times(3));
        checkBackoffSequence(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3);
    }

    private void assertExceptionIsThrown(String message, Callable<?> action) {
        try {
            action.call();
            fail(message);
        } catch (Exception e) {
        }
    }

    private void conditionsReturn(boolean shouldRetry) {
        when(queueRecoveryRetryCondition.test(nullable(RecordedQueue.class), nullable(Exception.class)))
            .thenReturn(shouldRetry);
        when(exchangeRecoveryRetryCondition.test(nullable(RecordedExchange.class), nullable(Exception.class)))
            .thenReturn(shouldRetry);
        when(bindingRecoveryRetryCondition.test(nullable(RecordedBinding.class), nullable(Exception.class)))
            .thenReturn(shouldRetry);
        when(consumerRecoveryRetryCondition.test(nullable(RecordedConsumer.class), nullable(Exception.class)))
            .thenReturn(shouldRetry);
    }

    private void verifyConditionsInvocation(VerificationMode mode) {
        verify(queueRecoveryRetryCondition, mode).test(nullable(RecordedQueue.class), any(Exception.class));
        verify(exchangeRecoveryRetryCondition, mode).test(nullable(RecordedExchange.class), any(Exception.class));
        verify(bindingRecoveryRetryCondition, mode).test(nullable(RecordedBinding.class), any(Exception.class));
        verify(consumerRecoveryRetryCondition, mode).test(nullable(RecordedConsumer.class), any(Exception.class));
    }

    private void verifyOperationsInvocation(VerificationMode mode) throws Exception {
        verify(queueRecoveryRetryOperation, mode).call(any(RetryContext.class));
        verify(exchangeRecoveryRetryOperation, mode).call(any(RetryContext.class));
        verify(bindingRecoveryRetryOperation, mode).call(any(RetryContext.class));
        verify(consumerRecoveryRetryOperation, mode).call(any(RetryContext.class));
    }

    private RetryHandler handler() {
        return handler(1);
    }

    private RetryHandler handler(int retryAttempts) {
        return new DefaultRetryHandler(
            queueRecoveryRetryCondition, exchangeRecoveryRetryCondition,
            bindingRecoveryRetryCondition, consumerRecoveryRetryCondition,
            queueRecoveryRetryOperation, exchangeRecoveryRetryOperation,
            bindingRecoveryRetryOperation, consumerRecoveryRetryOperation,
            retryAttempts,
            backoffPolicy);
    }

    private RetryContext retryContext() {
        return new RetryContext(null, new Exception(), null);
    }

    private void checkBackoffSequence(int... sequence) throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        verify(backoffPolicy, times(sequence.length))
            // for some reason Mockito calls the matchers twice as many times as the target method
            .backoff(intThat(i -> i == sequence[count.getAndIncrement() % sequence.length]));
    }
}
