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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2011-2015 Pivotal Software, Inc.  All rights reserved.

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

    public ConsumerWorkService(ExecutorService executor, ThreadFactory threadFactory, int shutdownTimeout) {
        this.privateExecutor = (executor == null);
        this.executor = (executor == null) ? Executors.newFixedThreadPool(DEFAULT_NUM_THREADS, threadFactory)
                                           : executor;
        this.workPool = new WorkPool<Channel, Runnable>();
        this.shutdownTimeout = shutdownTimeout;
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
