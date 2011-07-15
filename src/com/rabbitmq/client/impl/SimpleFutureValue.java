//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.

package com.rabbitmq.client.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A simple {@link Future Future&lt;V&gt;} implementation which allows
 * the future value to be set (once) explicitly. It is not possible
 * to interrupt or communicate with the task that sets it, so
 * {@link #cancel}, for example, always returns <code><b>false</b></code>.
 *
 * @param <V> the type of value returned by the {@link #get} methods
 * and accepted by the {@link #set} method.
 */
class SimpleFutureValue<V> implements Future<V> {

    private Object monitor = new Object();
    private V value = null;
    private boolean isSet = false;
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        synchronized (monitor) {
            return this.isSet;
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (monitor) {
            while (!this.isSet) {
                monitor.wait();
            }
            return value;
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        long timeoutInMillis = unit.toMillis(timeout);
        long future = System.currentTimeMillis() + timeoutInMillis;
        synchronized (monitor) {
            while (!this.isSet) {
                long remaining = future - System.currentTimeMillis();
                if (remaining <= 0)
                    throw new TimeoutException();
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException ie) {
                    // Ignore interruptions
                }
            }
            return value;
        }
    }

    /**
     * Set the future value for getters to retrieve, and release them if
     * they are waiting. The value can only be set once. If the future value is
     * already set this call will only release waiting getters.
     * @param value to set for future getters
     */
    public void set(V value) {
        synchronized (monitor) {
            if (!this.isSet) {
                this.value = value;
                this.isSet = true;
            }
            monitor.notifyAll();
        }
    }
}