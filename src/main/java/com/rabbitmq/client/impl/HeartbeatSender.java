// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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


package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Manages heartbeat sending for a {@link AMQConnection}.
 * <p/>
 * Heartbeats are sent in a dedicated thread that is separate
 * from the main loop thread used for the connection.
 */
final class HeartbeatSender {

    private final Object monitor = new Object();

    private final FrameHandler frameHandler;
    private final ThreadFactory threadFactory;

    private ScheduledExecutorService executor;
    private final boolean privateExecutor;

    private ScheduledFuture<?> future;

    private boolean shutdown = false;

    private volatile long lastActivityTime;

    HeartbeatSender(FrameHandler frameHandler, ScheduledExecutorService heartbeatExecutor, ThreadFactory threadFactory) {
        this.frameHandler = frameHandler;
        this.privateExecutor = (heartbeatExecutor == null);
        this.executor = heartbeatExecutor;
        this.threadFactory = threadFactory;
    }

    public void signalActivity() {
        this.lastActivityTime = System.nanoTime();
    }

    /**
     * Sets the heartbeat in seconds.
     */
    public void setHeartbeat(int heartbeatSeconds) {
        synchronized(this.monitor) {
            if(this.shutdown) {
                return;
            }

            // cancel any existing heartbeat task
            if(this.future != null) {
                this.future.cancel(true);
                this.future = null;
            }

            if (heartbeatSeconds > 0) {
                // wake every heartbeatSeconds / 2 to avoid the worst case
                // where the last activity comes just after the last heartbeat
                long interval = SECONDS.toNanos(heartbeatSeconds) / 2;
                ScheduledExecutorService executor = createExecutorIfNecessary();
                Runnable task = new HeartbeatRunnable(interval);
                this.future = executor.scheduleAtFixedRate(
                    task, interval, interval, TimeUnit.NANOSECONDS);
            }
        }
    }

    private ScheduledExecutorService createExecutorIfNecessary() {
        synchronized (this.monitor) {
            if (this.executor == null) {
                this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
            }
            return this.executor;
        }
    }

    /**
     * Shutdown the heartbeat process, if any.
     */
    public void shutdown() {
        ExecutorService executorToShutdown = null;
        synchronized (this.monitor) {
            if (this.future != null) {
                this.future.cancel(true);
                this.future = null;
            }

            if (this.privateExecutor) {
                // to be safe, we shouldn't call shutdown holding the
                // monitor.
                executorToShutdown = this.executor;
            }

            this.executor = null;
            this.shutdown = true;
        }
        if(executorToShutdown != null) {
            executorToShutdown.shutdown();
        }
    }

    private final class HeartbeatRunnable implements Runnable {

        private final long heartbeatNanos;

        private HeartbeatRunnable(long heartbeatNanos) {
            this.heartbeatNanos = heartbeatNanos;
        }

        @Override
        public void run() {
            try {
                long now = System.nanoTime();

                if (now > (lastActivityTime + this.heartbeatNanos)) {
                    frameHandler.writeFrame(new Frame(AMQP.FRAME_HEARTBEAT, 0));
                    frameHandler.flush();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
