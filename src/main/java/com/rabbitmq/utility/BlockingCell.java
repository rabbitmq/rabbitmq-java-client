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


package com.rabbitmq.utility;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Simple one-shot IPC mechanism. Essentially a one-place buffer that cannot be emptied once filled.
 */
public class BlockingCell<T> {

    private final AtomicReference<T> value = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private static final long NANOS_IN_MILLI = 1000L * 1000L;

    private static final long INFINITY = -1;

    /**
     * Wait for a value, and when one arrives, return it (without clearing it). If there's already a value present, there's no need to wait - the existing value
     * is returned.
     * @return the waited-for value
     *
     * @throws InterruptedException if this thread is interrupted
     */
    public T get() throws InterruptedException {
        this.latch.await();
        return this.value.get();
    }

    /**
     * Wait for a value, and when one arrives, return it (without clearing it). If there's
     * already a value present, there's no need to wait - the existing value is returned.
     * If timeout is reached and value hasn't arrived, TimeoutException is thrown.
     * 
     * @param timeout timeout in milliseconds. -1 effectively means infinity
     * @return the waited-for value
     * @throws InterruptedException if this thread is interrupted
     */
    public T get(long timeout) throws InterruptedException, TimeoutException {
        if (timeout == INFINITY) return get();

        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout cannot be less than zero");
        }

        boolean done = this.latch.await(timeout, MILLISECONDS);

        if (!done) {
            throw new TimeoutException();
        }

        return this.value.get();
    }

    /**
     * As get(), but catches and ignores InterruptedException, retrying until a value appears.
     * @return the waited-for value
     */
    public T uninterruptibleGet() {
        boolean wasInterrupted = false;
        try {
            while (true) {
                try {
                    return get();
                } catch (InterruptedException ex) {
                    // no special handling necessary
                    wasInterrupted = true;
                }
            }
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * As get(long timeout), but catches and ignores InterruptedException, retrying until
     * a value appears or until specified timeout is reached. If timeout is reached,
     * TimeoutException is thrown.
     * We also use System.nanoTime() to behave correctly when system clock jumps around.
     * 
     * @param timeout timeout in milliseconds. -1 means 'infinity': never time out
     * @return the waited-for value
     */
    public T uninterruptibleGet(int timeout) throws TimeoutException {
        long now = System.nanoTime() / NANOS_IN_MILLI;
        long runTime = now + timeout;
        boolean wasInterrupted = false;
        try {
            do {
                try {
                    return get(runTime - now);
                } catch (InterruptedException e) {
                    // Ignore.
                    wasInterrupted = true;
                }
            } while ((timeout == INFINITY) || ((now = System.nanoTime() / NANOS_IN_MILLI) < runTime));
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
        throw new TimeoutException();
    }

    /**
     * Store a value in this BlockingCell, throwing {@link IllegalStateException} if the cell already has a value.
     * @param newValue the new value to store
     */
    public void set(T newValue) {
        if (this.value.compareAndSet(null, newValue)) {
            this.latch.countDown();
        } else {
            throw new IllegalStateException("BlockingCell can only be set once");
        }
    }

    /**
     * Store a value in this BlockingCell if it doesn't already have a value.
     * @return true if this call to setIfUnset actually updated the BlockingCell; false if the cell already had a value.
     * @param newValue the new value to store
     */
    public boolean setIfUnset(T newValue) {
        if (this.value.compareAndSet(null, newValue)) {
            this.latch.countDown();
            return true;
        } else {
            return false;
        }
    }
}
