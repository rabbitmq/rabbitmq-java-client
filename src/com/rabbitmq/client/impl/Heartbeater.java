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
 * Manages heartbeats for a {@link AMQConnection}.
 * <p/>
 * Heartbeats are sent in a dedicated thread that is separate
 * from the main loop thread used for the connection.
 */
final class Heartbeater {

    private final Object monitor = new Object();

    private final FrameHandler frameHandler;

    private ScheduledExecutorService executor;

    private ScheduledFuture<?> future;

    private volatile long lastActivityTime;

    Heartbeater(FrameHandler frameHandler) {
        this.frameHandler = frameHandler;
    }

    public void signalActivity() {
        this.lastActivityTime = System.nanoTime();
    }

    /**
     * Sets the heartbeat in seconds.
     */
    public void setHeartbeat(int heartbeatSeconds) {
        ScheduledFuture<?> previousFuture;
        synchronized (this.monitor) {
            previousFuture = this.future;
            this.future = null;
        }

        if (previousFuture != null) {
            previousFuture.cancel(true);
        }

        if (heartbeatSeconds > 0) {
            // wake every heartbeatSeconds / 2 to avoid the worst case
            // where the last activity comes just after the last heartbeat
            long interval = SECONDS.toMillis(heartbeatSeconds) / 2;
            ScheduledExecutorService executor = createExecutorIfNecessary();
            Runnable task = new HeartbeatRunnable(interval);
            ScheduledFuture<?> newFuture = executor.scheduleAtFixedRate(
                    task, interval, interval, TimeUnit.MILLISECONDS);

            synchronized (this.monitor) {
                this.future = newFuture;
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
        ScheduledFuture<?> future;
        ScheduledExecutorService executor;

        synchronized (this.monitor) {
            future = this.future;
            executor = this.executor;
            this.future = null;
            this.executor = null;
        }

        if (future != null) {
            future.cancel(true);
        }

        if (executor != null) {
            executor.shutdown();
        }
    }

    private class HeartbeatRunnable implements Runnable {

        private final long heartbeatNanos;

        private HeartbeatRunnable(long heartbeatMillis) {
            this.heartbeatNanos = MILLISECONDS.toNanos(heartbeatMillis);
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
