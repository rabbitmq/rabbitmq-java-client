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
 * This is a generic implementation of the <q>Channels</q> specification
 * in <i>Channeling Work</i>, Nov 2010 (<tt>channels.pdf</tt>).
 * <p/>
 * Items (type W) are queued for a collection of clients (type K).
 * Clients may be registered to have a queue of items stored.
 * <p/>
 * Each client has a distinct queue and is exactly one of <i>dormant</i>,
 * <i>in progress</i> or <i>ready</i>.
 * <p/>
 * Items may be (singly) added to (the end of) a client&apos;s queue.
 * A <i>ready</i> client, and a batch of its items,
 * may be retrieved (placing that client <i>in progress</i>).
 * A client can finish (processing a batch of items).
 * <p/>
 * If a client has items queued, it is either <i>in progress</i> or <i>ready</i> but cannot be both.
 * When work is finished it may be marked <i>ready</i> if there is further work,
 * or <i>dormant</i> if there is not.
 * There is never any work for a <i>dormant</i> client.
 * <p/>
 * <b>Concurrent Semantics</b><br/>
 * This implementation is thread-safe.
 * <p/>
 * <b>Implementation Notes</b><br/>
 * The state is, roughly, as follows:
 * <pre> pool :: <i>map</i>(K, <i>seq</i> W)
 * inProgress :: <i>set</i> K
 * ready :: <i>iseq</i> K</pre>
 * <p/>
 * where a <code><i>seq</i></code> is a sequence (queue or list) and an <code><i>iseq</i></code>
 * (<i>i</i> for <i>i</i>njective) is a sequence with no duplicates.
 * <p/>
 * <b>State transitions</b><br/><pre>
 *      finish(k)            -------------
 *             -----------> | (dormant)   |
 *            |              -------------
 *  -------------  next()        | add(item)
 * | in progress | <---------    |
 *  -------------            |   V
 *            |              -------------
 *             -----------> | ready       |
 *      finish(k)            -------------
 * </pre>
 * <i>dormant</i> is not represented in the implementation state, and adding items
 * when the client is <i>in progress</i> or <i>ready</i> does not change its state.
 */
public class WorkPool<K, W> {

    /** protecting <code>ready</code>, <code>inProgress</code> and <code>pool</code> */
    private final Object monitor = new Object();
        private final SetQueue<K> ready = new SetQueue<K>();
        private final Set<K> inProgress = new HashSet<K>();
        private final Map<K, BlockingQueue<W>> pool = new HashMap<K, BlockingQueue<W>>();

    /**
     * Add client <code><b>key</b></code> to pool of item queues, with an empty queue.
     * A client is initially <i>dormant</i>.
     * <p/>
     * No-op if <code><b>key</b></code> already present. 
     * @param key client to add to pool
     */
    public void registerKey(K key) {
        synchronized (this.monitor) {
            if (!this.pool.containsKey(key)) {
                this.pool.put(key, new LinkedBlockingQueue<W>());
            }
        }
    }

    /**
     * Return the next <i>ready</i> client,
     * and transfer a collection of items for that client to process.
     * Mark client <i>in progress</i>.
     * <p/>
     * If there is no ready client, return null.
     * @param to collection object in which to transfer items
     * @param size max number of items to transfer
     * @return key of client to whom items belong, or null if there is none.
     */
    public K nextWorkBlock(Collection<W> to, int size) {
        synchronized (this.monitor) {
            K nextKey = readyToInProgress();
            if (nextKey != null) {
                BlockingQueue<W> queue = this.pool.get(nextKey);
                queue.drainTo(to, size);
            }
            return nextKey;
        }
    }

    /**
     * Add (enqueue) an item for a specific client.
     * If <i>dormant</i>, the client will be marked <i>ready</i>.
     * @param key the client to add to the work item to
     * @param item the work item to add to the client queue
     * @return <code><b>true</b></code> if and only if the client is marked <i>ready</i>
     * &mdash; <i>as a result of this work item</i>
     * @throws IllegalArgumentException if the client is not registered
     */
    public boolean addWorkItem(K key, W item) {
        synchronized (this.monitor) {
            Queue<W> queue = this.pool.get(key);
            if (queue == null) {
                throw new IllegalArgumentException("Unknown client");
            }
            queue.offer(item);
            if (dormant(key)) {
                dormantToReady(key);
                return true;
            }
            return false;
        }
    }

    /**
     * Report client no longer <i>in progress</i>.
     * @param key client that has finished work
     * @return true if and only if client becomes <i>ready</i>
     */
    public boolean finishWorkBlock(K key) {
        synchronized (this.monitor) {
            if (!this.inProgress.contains(key)) {
                throw new IllegalStateException("Client not in progress.");
            }

            if (moreWorkItems(key)) {
                inProgressToReady(key);
                return true;
            } else {
                inProgressToDormant(key);
                return false;
            }
        }
    }

    private boolean inProgress(K key){ return this.inProgress.contains(key); }
    private boolean ready(K key){ return this.ready.contains(key); }
    private boolean dormant(K key){ return !inProgress(key) && !ready(key); }

    private void inProgressToReady(K key){
        this.inProgress.remove(key);
        this.ready.addIfNotPresent(key);
    };

    private void inProgressToDormant(K key){
        this.inProgress.remove(key);
    };

    private void dormantToReady(K key){
        this.ready.addIfNotPresent(key);
    };

    private K readyToInProgress(){
        K key = this.ready.poll();
        if (key != null) {
            this.inProgress.add(key);
        }
        return key;
    };

    private boolean moreWorkItems(K key) {
        return !this.pool.get(key).isEmpty();
    }
}
