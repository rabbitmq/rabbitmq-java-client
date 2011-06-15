package com.rabbitmq.client.impl;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * This is a generic implementation of the <q>Channels</q> specification
 * in <i>Channeling Work</i>, Nov 2010 (<tt>channels.pdf</tt>).
 * <p/>
 * Objects of type <b>K</b> must be registered, then they become <i>clients</i> and a queue of
 * items (type <b>W</b>) is stored for each client.
 * <p/>
 * Each client has a <i>state</i> which is exactly one of <i>dormant</i>,
 * <i>in progress</i> or <i>ready</i>. Initially (after registration) a client is <i>dormant</i>.
 * <p/>
 * Items may be (singly) added to (the end of) a client&apos;s queue with <code><b>addWorkItem()</b></code>.
 * If the client is <i>dormant</i> it becomes <i>ready</i> thereby. All other states remain unchanged.
 * <p/>
 * The next <i>ready</i> client, and a collection of its items,
 * may be retrieved with <code><b>nextWorkBlock()</b></code> (making that client <i>in progress</i>).
 * <p/>
 * An <i>in progress</i> client can finish (processing a batch of items) with <code><b>finishWorkBlock()</b></code>.
 * It then becomes either <i>dormant</i> or <i>ready</i>, depending if its queue of work items is empty.
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

    public WorkPool() {}

    /** protecting <code>ready</code>, <code>inProgress</code> and <code>pool</code> */
    private final Object monitor = new Object();
        /** An ordered queue of <i>ready</i> clients. */
        private final SetQueue<K> ready = new SetQueue<K>();
        /** The set of clients which have work <i>in progress</i>. */
        private final Set<K> inProgress = new HashSet<K>();
        /** The pool of registered clients, with their work queues. */
        private final Map<K, Deque<W>> pool = new HashMap<K, Deque<W>>();

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
                this.pool.put(key, new LinkedList<W>());
            }
        }
    }

    /**
     * Return the next <i>ready</i> client,
     * and transfer a collection of that client's items to process.
     * Mark client <i>in progress</i>.
     * <p/>
     * If there is no <i>ready</i> client, return <code><b>null</b></code>.
     * @param to collection object in which to transfer items
     * @param size max number of items to transfer
     * @return key of client to whom items belong, or <code><b>null</b></code> if there is none.
     */
    public K nextWorkBlock(Collection<W> to, int size) {
        synchronized (this.monitor) {
            K nextKey = readyToInProgress();
            if (nextKey != null) {
                Deque<W> queue = this.pool.get(nextKey);
                drainTo(queue, to, size);
            }
            return nextKey;
        }
    }

    /**
     * Private implementation of <code><b>drainTo</b></code> (not implemented for <code><b>Deque&lt;W&gt;</b></code>s).
     * @param <W> element type
     * @param deque to take (poll) elements from
     * @param c to add elements to
     * @param maxElements to take from deque
     * @return number of elements actually taken
     */
    private static <W> int drainTo(Deque<W> deque, Collection<W> c, int maxElements) {
        int n = 0;
        while (n < maxElements) {
            W first = deque.poll();
            if (first == null)
                break;
            c.add(first);
            ++n;
        }
        return n;
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
            if (isDormant(key)) {
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

    private boolean moreWorkItems(K key) {
        return !this.pool.get(key).isEmpty();
    }
    
    /* State identification functions */
    private boolean isInProgress(K key){ return this.inProgress.contains(key); }
    private boolean isReady(K key){ return this.ready.contains(key); }
    private boolean isDormant(K key){ return !isInProgress(key) && !isReady(key); }

    /* State transition methods */
    private void inProgressToReady(K key){ this.inProgress.remove(key); this.ready.addIfNotPresent(key); };
    private void inProgressToDormant(K key){ this.inProgress.remove(key); };
    private void dormantToReady(K key){ this.ready.addIfNotPresent(key); };

    private K readyToInProgress() {
        K key = this.ready.poll();
        if (key != null) {
            this.inProgress.add(key);
        }
        return key;
    };
}
