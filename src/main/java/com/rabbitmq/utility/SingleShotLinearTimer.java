// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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


package com.rabbitmq.utility;

import com.rabbitmq.client.impl.AMQChannel;

/**
 * This class provides a very stripped-down clone of some of the functionality in
 * java.util.Timer (notably Timer.schedule(TimerTask task, long delay) but 
 * uses System.nanoTime() rather than System.currentTimeMillis() as a measure
 * of the underlying time, and thus behaves correctly if the system clock jumps
 * around.
 * 
 * This class does not have any relation to TimerTask due to the coupling 
 * between TimerTask and Timer - for example if someone invokes 
 * TimerTask.cancel(), we can't find out about it as TimerTask.state is
 * package-private.
 * 
 * We currently just use this to time the quiescing RPC in AMQChannel.
 *
 * Will be removed in next major release.
 *
 * @deprecated
 * @see AMQChannel
 */

public class SingleShotLinearTimer {
    private volatile Runnable _task;
    private Thread _thread;

    public synchronized void schedule(Runnable task, int timeoutMillisec) {
        if (task == null) {
            throw new IllegalArgumentException("Don't schedule a null task");
        }
        
        if (_task != null) {
            throw new UnsupportedOperationException("Don't schedule more than one task");
        }

        if (timeoutMillisec < 0) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        
        _task = task;
        
        _thread = new Thread(new TimerThread(timeoutMillisec));
        _thread.setDaemon(true);
        _thread.start();
    }
    
    private static final long NANOS_IN_MILLI = 1000 * 1000;
    
    private class TimerThread implements Runnable {
        private final long _runTime;
        
        public TimerThread(long timeoutMillisec) {
            _runTime = System.nanoTime() / NANOS_IN_MILLI + timeoutMillisec;
        }

        @Override
        public void run() {
            try {
                long now;
                boolean wasInterrupted = false;
                try {
                    while ((now = System.nanoTime() / NANOS_IN_MILLI) < _runTime) {
                        if (_task == null) break;

                        try {
                            synchronized(this) {
                                wait(_runTime - now);
                            }
                        } catch (InterruptedException e) {
                            wasInterrupted = true;
                        }
                    }
                } finally {
                    if (wasInterrupted)
                        Thread.currentThread().interrupt();
                }
                
                Runnable task = _task;
                if (task != null) {
                    task.run();
                }

            } finally {
                _task = null;
            }
        }
    }

    public void cancel() {
        _task = null;
    }
}
