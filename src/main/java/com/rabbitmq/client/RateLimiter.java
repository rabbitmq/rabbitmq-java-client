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

/**
 * Interface for rate limiting publisher confirmations.
 * <p>
 * Implementations control the rate of publish operations by acquiring permits
 * before publishing and releasing them when confirmations are received.
 * <p>
 * The library provides {@link ThrottlingRateLimiter} as a default implementation
 * with progressive throttling behavior. Users can implement custom rate limiting
 * strategies by implementing this interface.
 *
 * @see ThrottlingRateLimiter
 */
public interface RateLimiter
{
    /**
     * Acquires a permit, blocking if necessary until one is available.
     * <p>
     * Implementations may apply delays or other backpressure mechanisms
     * before returning the permit.
     *
     * @return A permit that must be released when the operation completes
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    Permit acquire() throws InterruptedException;

    /**
     * Gets the maximum concurrency supported by this rate limiter.
     * <p>
     * This is a hint for sizing internal data structures. Implementations
     * that don't have a fixed capacity should return 0.
     *
     * @return the maximum number of concurrent operations, or 0 if unknown/unlimited
     */
    default int getMaxConcurrency()
    {
        return 0;
    }

    /**
     * A permit that represents acquired access to a rate-limited resource.
     * Must be released when the operation completes.
     */
    interface Permit
    {
        /**
         * Releases this permit, returning it to the rate limiter pool.
         */
        void release();
    }
}
