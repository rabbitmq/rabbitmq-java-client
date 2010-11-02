package com.rabbitmq.client.impl;

import java.util.*;

/**
 * @author robharrop
 */
public class WorkPool<K> {

    private final Object monitor = new Object();

    // TODO: replace with TreeSet? BlockingQueue?
    private final SetQueue<K> ready = new SetQueue<K>();

    private final Set<K> inProgress = new HashSet<K>();

    private final Map<K, Queue<Runnable>> pool = new HashMap<K, Queue<Runnable>>();

    public void registerKey(K key) {
        synchronized (this.monitor) {
            if (!this.pool.containsKey(key)) {
                this.pool.put(key, new LinkedList<Runnable>());
            }
        }
    }

    public WorkBlock<K> nextBlock() throws InterruptedException {
        synchronized (this.monitor) {
            K nextKey = this.ready.take();
            Runnable nextRunnable = this.pool.get(nextKey).poll();
            markInProgress(nextKey);
            return new WorkBlock<K>(nextKey, nextRunnable);
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

    public boolean workBlockFinished(WorkBlock<K> workBlock) {
        synchronized(this.monitor) {
            K key = workBlock.getKey();

            if(!this.inProgress.contains(key)) {
                throw new IllegalStateException("WorkBlock already completed.");
            }

            this.inProgress.remove(key);
            return markReadyIfPossible(key);
        }
    }

    private boolean markReadyIfPossible(K key) {
        if (!this.inProgress.contains(key)) {
            if(!this.pool.get(key).isEmpty()) {
                this.ready.push(key);
                return true;
            }
        }
        return false;
    }

    private void markInProgress(K key) {
        this.inProgress.add(key);
    }

    public static final class WorkBlock<K> {
        private final K key;
        private final Runnable runnable;

        public WorkBlock(K key, Runnable runnable) {
            this.key = key;
            this.runnable = runnable;
        }

        public K getKey() {
            return key;
        }

        public Runnable getRunnable() {
            return runnable;
        }
    }
}
