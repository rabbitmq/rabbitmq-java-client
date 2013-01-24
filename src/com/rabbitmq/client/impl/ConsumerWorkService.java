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
//  Copyright (c) 2011-2013 VMware, Inc.  All rights reserved.

package com.rabbitmq.client.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.rabbitmq.client.Channel;

final class ConsumerWorkService {
    private static final int MAX_RUNNABLE_BLOCK_SIZE = 16;
    private static final int DEFAULT_NUM_THREADS = 5;
    private final ExecutorService executor;
    private final boolean privateExecutor;
    private final WorkPool<Channel, Runnable> workPool;

    public ConsumerWorkService(ExecutorService executor) {
        this.privateExecutor = (executor == null);
        this.executor = (executor == null) ? Executors.newFixedThreadPool(DEFAULT_NUM_THREADS)
                                           : executor;
        this.workPool = new WorkPool<Channel, Runnable>();
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

    public void addWork(Channel channel, Runnable runnable) {
        if (this.workPool.addWorkItem(channel, runnable)) {
            this.executor.execute(new WorkPoolRunnable());
        }
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
