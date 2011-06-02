package com.rabbitmq.client.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author robharrop
 */
public class WorkPool<K> {

    private final Object monitor = new Object();

    // TODO: replace with TreeSet? BlockingQueue?
    private final SetQueue<K> ready = new SetQueue<K>();

    private final Set<K> inProgress = new HashSet<K>();

    private final Map<K, BlockingQueue<Runnable>> pool = new HashMap<K, BlockingQueue<Runnable>>();

    public void registerKey(K key) {
        synchronized (this.monitor) {
            if (!this.pool.containsKey(key)) {
                this.pool.put(key, new LinkedBlockingQueue<Runnable>());
            }
        }
    }

    public K nextBlock(Collection<Runnable> to, int size) throws InterruptedException {
        synchronized (this.monitor) {
            K nextKey = this.ready.take();
            markInProgress(nextKey);

            BlockingQueue<Runnable> queue = this.pool.get(nextKey);
            queue.drainTo(to, size);

            return nextKey;
        }
    }

    public boolean workIn(K key, Runnable runnable) {

        synchronized (this.monitor) {
            Queue<Runnable> queue = this.pool.get(key);
            if (queue == null) {
                throw new IllegalArgumentException("Unknown key");
            }
            queue.offer(runnable);
            return markReadyIfPossible(key);
        }
    }

    public boolean workBlockFinished(K key) {
        synchronized (this.monitor) {
            if (!this.inProgress.contains(key)) {
                throw new IllegalStateException("WorkBlock already completed.");
            }

            this.inProgress.remove(key);
            return markReadyIfPossible(key);
        }
    }

    private boolean markReadyIfPossible(K key) {
        if (!this.inProgress.contains(key)) {
            if (!this.pool.get(key).isEmpty()) {
                return this.ready.push(key);
            }
        }
        return false;
    }

    private void markInProgress(K key) {
        this.inProgress.add(key);
    }

}
