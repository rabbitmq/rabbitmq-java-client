package com.rabbitmq.client.impl;

import com.rabbitmq.client.ThreadFactory;

/**
 * Default thread factory that instantiates {@link java.lang.Thread}s directly.
 */
public class DefaultThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r, String threadName) {
        return new Thread(r, threadName);
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r);
    }
}
