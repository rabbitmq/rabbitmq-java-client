package com.rabbitmq.client.impl;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

public class ShutdownThreadPoolExecutorFactory {

    public ThreadPoolExecutor create(final ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new IllegalArgumentException();
        }
        return new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                return Environment.newThread(threadFactory, r, "ConsumerWorkService shutdown monitor", true);
            }
        });
    }

}
