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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.rabbitmq.client.Channel;

final public class ConsumerWorkService {
    private static final int MAX_RUNNABLE_BLOCK_SIZE = 16;
    private static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private final ExecutorService executor;
    private final boolean privateExecutor;
    private final WorkPool<Channel, Runnable> workPool;
    private final int shutdownTimeout;

    public ConsumerWorkService(ExecutorService executor, ThreadFactory threadFactory, int queueingTimeout, int shutdownTimeout) {
        this.privateExecutor = (executor == null);
        this.executor = (executor == null) ? Executors.newFixedThreadPool(DEFAULT_NUM_THREADS, threadFactory)
                                           : executor;
        this.workPool = new WorkPool<>(queueingTimeout);
        this.shutdownTimeout = shutdownTimeout;
    }

    public ConsumerWorkService(ExecutorService executor, ThreadFactory threadFactory, int shutdownTimeout) {
        this(executor, threadFactory, -1, shutdownTimeout);
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Stop executing all consumer work
     */
    public void shutdown() {
        this.workPool.unregisterAllKeys();
        if (privateExecutor)
            this.executor.shutdown();
    }

    /**
     * Stop executing all consumer work for a particular channel
     * @param channel to stop consumer work for
     */
    public void stopWork(Channel channel) {
        this.workPool.unregisterKey(channel);
    }

    public void registerKey(Channel channel) {
        this.workPool.registerKey(channel);
    }

    public void setUnlimited(Channel channel, boolean unlimited) {
        if (unlimited) {
            this.workPool.unlimit(channel);
        } else {
            this.workPool.limit(channel);
        }
    }

    public void addWork(Channel channel, Runnable runnable) {
        if (this.workPool.addWorkItem(channel, runnable)) {
            this.executor.execute(new WorkPoolRunnable());
        }
    }

    /**
     * @return true if executor used by this work service is managed
     *              by it and wasn't provided by the user
     */
    public boolean usesPrivateExecutor() {
        return privateExecutor;
    }

    private final class WorkPoolRunnable implements Runnable {

        @Override
        public void run() {
            int size = MAX_RUNNABLE_BLOCK_SIZE;
            List<Runnable> block = new ArrayList<Runnable>(size);
            try {
                Channel key = ConsumerWorkService.this.workPool.nextWorkBlock(block, size);
                if (key == null) return; // nothing ready to run
                try {
                    for (Runnable runnable : block) {
                        runnable.run();
                    }
                } finally {
                    if (ConsumerWorkService.this.workPool.finishWorkBlock(key)) {
                        ConsumerWorkService.this.executor.execute(new WorkPoolRunnable());
                    }
                }
            } catch (RuntimeException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
