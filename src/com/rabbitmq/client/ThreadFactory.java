package com.rabbitmq.client;

/**
 * Extends {@link java.util.concurrent.ThreadFactory} to make it possible to specify
 * thread name.
 *
 * In environments with restricted thread management (e.g. Google App Engine), developers
 * can provide a custom factory to control how network I/O thread is created.
 */
public interface ThreadFactory extends java.util.concurrent.ThreadFactory {
    /**
     * Like {@link java.util.concurrent.ThreadFactory#newThread(Runnable)} but also takes
     * a thread name.
     *
     * @param r runnable to execute
     * @param threadName thread name
     * @return a new thread
     */
    Thread newThread(Runnable r, String threadName);
}
