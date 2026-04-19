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

package com.rabbitmq.client.test;

import com.rabbitmq.client.ThrottlingRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ThrottlingRateLimiterTest
{

    @Test public void testPermitAcquireAndRelease() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 50);

        assertEquals(10, limiter.getAvailablePermits());

        ThrottlingRateLimiter.Permit permit = limiter.acquire();
        assertEquals(9, limiter.getAvailablePermits());

        permit.release();
        assertEquals(10, limiter.getAvailablePermits());
    }

    @Test public void testMultipleAcquireAndRelease() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 50);

        List<ThrottlingRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            permits.add(limiter.acquire());
        }

        assertEquals(5, limiter.getAvailablePermits());

        for (ThrottlingRateLimiter.Permit p : permits) {
            p.release();
        }

        assertEquals(10, limiter.getAvailablePermits());
    }

    @Test public void testThrottlingOccursAboveThreshold() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 50);

        // Hold 6 permits (60% used, above 50% threshold)
        List<ThrottlingRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            permits.add(limiter.acquire());
        }

        assertEquals(4, limiter.getAvailablePermits());

        // Next acquire should throttle - verify with lenient timing
        long start = System.currentTimeMillis();
        ThrottlingRateLimiter.Permit permit = limiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed > 10, "Should apply throttling delay, got " + elapsed + "ms");
        assertEquals(3, limiter.getAvailablePermits());

        permit.release();
        for (ThrottlingRateLimiter.Permit p : permits) {
            p.release();
        }
    }

    @Test public void testHighThrottlingNearCapacity() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 50);

        // Hold 9 permits (90% used)
        List<ThrottlingRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            permits.add(limiter.acquire());
        }

        assertEquals(1, limiter.getAvailablePermits());

        // Should have significant delay
        long start = System.currentTimeMillis();
        ThrottlingRateLimiter.Permit permit = limiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed > 500, "Should have significant delay near capacity, got " + elapsed + "ms");
        assertEquals(0, limiter.getAvailablePermits());

        permit.release();
        for (ThrottlingRateLimiter.Permit p : permits) {
            p.release();
        }
    }

    @Test public void testThrottlingThresholds() throws InterruptedException
    {
        // 80% threshold - more permissive
        ThrottlingRateLimiter limiter80 = new ThrottlingRateLimiter(10, 80);

        ThrottlingRateLimiter.Permit permit1 = limiter80.acquire();
        assertEquals(9, limiter80.getAvailablePermits());

        ThrottlingRateLimiter.Permit permit2 = limiter80.acquire();
        assertEquals(8, limiter80.getAvailablePermits());

        permit1.release();
        permit2.release();

        // 20% threshold - more restrictive
        ThrottlingRateLimiter limiter20 = new ThrottlingRateLimiter(10, 20);

        // Hold 9 permits (90% used, well above 20% threshold)
        List<ThrottlingRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            permits.add(limiter20.acquire());
        }

        assertEquals(1, limiter20.getAvailablePermits());

        // Should throttle
        long start = System.currentTimeMillis();
        ThrottlingRateLimiter.Permit permit = limiter20.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed > 10, "Should throttle above 20% threshold, got " + elapsed + "ms");

        permit.release();
        for (ThrottlingRateLimiter.Permit p : permits) {
            p.release();
        }
    }

    @Test public void testConcurrentAcquireAndRelease() throws InterruptedException, ExecutionException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(50, 50);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(executor.submit(() -> {
                try {
                    ThrottlingRateLimiter.Permit permit = limiter.acquire();
                    successCount.incrementAndGet();
                    Thread.sleep(10);
                    permit.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(100, successCount.get());
        assertEquals(50, limiter.getAvailablePermits());
    }

    @Test public void testZeroThresholdBehavior() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 0);

        // 0% threshold means threshold = 0
        // Available = 10, threshold = 0, so 10 >= 0 = no throttle
        ThrottlingRateLimiter.Permit permit = limiter.acquire();
        assertEquals(9, limiter.getAvailablePermits());
        permit.release();
    }

    @Test public void testHundredPercentThreshold() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 100);

        // 100% threshold means threshold = 10
        // Hold 9 permits: available = 1, threshold = 10, so 1 < 10 = throttle
        List<ThrottlingRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            permits.add(limiter.acquire());
        }

        assertEquals(1, limiter.getAvailablePermits());

        long start = System.currentTimeMillis();
        ThrottlingRateLimiter.Permit permit = limiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed > 500, "Should throttle with 100% threshold near capacity, got " + elapsed + "ms");

        permit.release();
        for (ThrottlingRateLimiter.Permit p : permits) {
            p.release();
        }
    }

    @Test public void testGetStatistics() throws InterruptedException
    {
        ThrottlingRateLimiter limiter = new ThrottlingRateLimiter(10, 50);

        assertEquals(10, limiter.getAvailablePermits());

        ThrottlingRateLimiter.Permit p1 = limiter.acquire();
        assertEquals(9, limiter.getAvailablePermits());

        ThrottlingRateLimiter.Permit p2 = limiter.acquire();
        assertEquals(8, limiter.getAvailablePermits());

        p1.release();
        assertEquals(9, limiter.getAvailablePermits());

        p2.release();
        assertEquals(10, limiter.getAvailablePermits());
    }
}
