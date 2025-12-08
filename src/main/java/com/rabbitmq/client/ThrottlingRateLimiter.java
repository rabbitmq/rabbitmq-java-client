// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A rate limiter that controls the rate of operations by limiting concurrency and applying delays
 * when a specified threshold of concurrency usage is reached.
 * <p>
 * The delay algorithm checks the current available permits. If the available permits are greater than or equal
 * to the throttling threshold, no delay is applied. Otherwise, it calculates a delay based on the percentage
 * of permits used, scaling it up to a maximum of 1000 milliseconds.
 * <p>
 * This implementation matches the behavior of the .NET client's {@code ThrottlingRateLimiter}.
 *
 * @see RateLimiter
 */
public class ThrottlingRateLimiter implements RateLimiter
{

    /**
     * The default throttling percentage, which defines the threshold for applying throttling, set to 50%.
     */
    public static final int DEFAULT_THROTTLING_PERCENTAGE = 50;

    private final int maxConcurrency;
    private final int throttlingThreshold;
    private final Semaphore semaphore;
    private final AtomicInteger currentPermits;

    /**
     * Initializes a new instance with the specified maximum number of concurrent calls
     * and the default throttling percentage (50%).
     *
     * @param maxConcurrentCalls The maximum number of concurrent operations allowed
     */
    public ThrottlingRateLimiter(int maxConcurrentCalls) {
        this(maxConcurrentCalls, DEFAULT_THROTTLING_PERCENTAGE);
    }

    /**
     * Initializes a new instance with the specified maximum number of concurrent calls
     * and throttling percentage.
     *
     * @param maxConcurrentCalls The maximum number of concurrent operations allowed
     * @param throttlingPercentage The percentage of maxConcurrentCalls at which throttling is triggered
     */
    public ThrottlingRateLimiter(int maxConcurrentCalls, int throttlingPercentage) {
        if (maxConcurrentCalls <= 0) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        }
        if (throttlingPercentage < 0 || throttlingPercentage > 100) {
            throw new IllegalArgumentException("throttlingPercentage must be between 0 and 100");
        }

        this.maxConcurrency = maxConcurrentCalls;
        this.throttlingThreshold = maxConcurrency * throttlingPercentage / 100;
        this.semaphore = new Semaphore(maxConcurrentCalls, true);
        this.currentPermits = new AtomicInteger(maxConcurrentCalls);
    }

    /**
     * Acquires a permit, blocking if necessary until one is available.
     * Applies throttling delay if the number of available permits falls below the threshold.
     *
     * @return A permit that must be released when the operation completes
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public Permit acquire() throws InterruptedException {
        int delay = calculateDelay();
        if (delay > 0) {
            Thread.sleep(delay);
        }

        semaphore.acquire();
        currentPermits.decrementAndGet();
        return new Permit(this);
    }

    /**
     * Releases a permit, returning it to the pool.
     */
    private void release() {
        currentPermits.incrementAndGet();
        semaphore.release();
    }

    /**
     * Gets the current number of available permits.
     *
     * @return The number of permits currently available
     */
    public int getAvailablePermits() {
        return currentPermits.get();
    }

    /**
     * Gets the maximum concurrency supported by this rate limiter.
     *
     * @return The maximum number of concurrent operations allowed
     */
    @Override
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    private int calculateDelay() {
        int availablePermits = currentPermits.get();

        if (availablePermits >= throttlingThreshold) {
            // No delay - available permits exceed the threshold
            return 0;
        }

        double percentageUsed = 1.0 - (availablePermits / (double) maxConcurrency);
        return (int)(percentageUsed * 1000);
    }

    /**
     * A permit that represents acquired access to a rate-limited resource.
     * Must be released when the operation completes.
     */
    public static class Permit implements RateLimiter.Permit {
        private final ThrottlingRateLimiter limiter;
        private boolean released = false;

        private Permit(ThrottlingRateLimiter limiter) {
            this.limiter = limiter;
        }

        /**
         * Releases this permit, returning it to the rate limiter pool.
         */
        public void release() {
            if (!released) {
                released = true;
                limiter.release();
            }
        }
    }
}
