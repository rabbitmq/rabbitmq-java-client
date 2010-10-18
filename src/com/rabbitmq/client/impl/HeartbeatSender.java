//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.*;

/**
 * Manages heartbeat sending for a {@link AMQConnection}.
 * <p/>
 * Heartbeats are sent in a dedicated thread that is separate
 * from the main loop thread used for the connection.
 */
final class HeartbeatSender {

    private final Object monitor = new Object();

    private final FrameHandler frameHandler;

    private ScheduledExecutorService executor;

    private ScheduledFuture<?> future;

    private boolean shutdown = false;

    private volatile long lastActivityTime;

    HeartbeatSender(FrameHandler frameHandler) {
        this.frameHandler = frameHandler;
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
                    task, interval, interval, TimeUnit.MILLISECONDS);
            }
        }
    }

    private ScheduledExecutorService createExecutorIfNecessary() {
        synchronized (this.monitor) {
            if (this.executor == null) {
                this.executor = Executors.newSingleThreadScheduledExecutor();
            }
            return this.executor;
        }
    }

    /**
     * Shutdown the heartbeat process, if any.
     */
    public void shutdown() {
        ScheduledExecutorService executorToShutdown = null;
        synchronized (this.monitor) {
            if (this.future != null) {
                this.future.cancel(true);
                this.future = null;
            }

            if (this.executor != null) {
                // to be safe, we shouldn't call shutdown holding the
                // monitor.
                executorToShutdown = this.executor;

                this.shutdown = true;
                this.executor = null;
            }
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

        public void run() {
            try {
                long now = System.nanoTime();

                if (now > (lastActivityTime + this.heartbeatNanos)) {
                    frameHandler.writeFrame(new Frame(AMQP.FRAME_HEARTBEAT, 0));
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
