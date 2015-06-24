package com.rabbitmq.client.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This is a generic implementation of the <q>Channels</q> specification
 * in <i>Channeling Work</i>, Nov 2010 (<tt>channels.pdf</tt>).
 * <p/>
 * Objects of type <b>K</b> must be registered, with <code><b>registerKey(K)</b></code>,
 * and then they become <i>clients</i> and a queue of
 * items (type <b>W</b>) is stored for each client.
 * <p/>
 * Each client has a <i>state</i> which is exactly one of <i>dormant</i>,
 * <i>in progress</i> or <i>ready</i>. Immediately after registration a client is <i>dormant</i>.
 * <p/>
 * Items may be (singly) added to (the end of) a client&apos;s queue with <code><b>addWorkItem(K,W)</b></code>.
 * If the client is <i>dormant</i> it becomes <i>ready</i> thereby. All other states remain unchanged.
 * <p/>
 * The next <i>ready</i> client, together with a collection of its items,
 * may be retrieved with <code><b>nextWorkBlock(collection,max)</b></code>
 * (making that client <i>in progress</i>).
 * <p/>
 * An <i>in progress</i> client can finish (processing a batch of items) with <code><b>finishWorkBlock(K)</b></code>.
 * It then becomes either <i>dormant</i> or <i>ready</i>, depending if its queue of work items is empty or no.
 * <p/>
 * If a client has items queued, it is either <i>in progress</i> or <i>ready</i> but cannot be both.
 * When work is finished it may be marked <i>ready</i> if there is further work,
 * or <i>dormant</i> if there is not.
 * There is never any work for a <i>dormant</i> client.
 * <p/>
 * A client may be unregistered, with <code><b>unregisterKey(K)</b></code>, which removes the client from
 * all parts of the state, and any queue of items stored with it.
 * All clients may be unregistered with <code><b>unregisterAllKeys()</b></code>.
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
 * @param <K> Key -- type of client
 * @param <W> Work -- type of work item
 */
public class WorkPool<K, W> {
    private static final int MAX_QUEUE_LENGTH = 1000;

    /** An injective queue of <i>ready</i> clients. */
    private final SetQueue<K> ready = new SetQueue<K>();
    /** The set of clients which have work <i>in progress</i>. */
    private final Set<K> inProgress = new HashSet<K>();
    /** The pool of registered clients, with their work queues. */
    private final Map<K, VariableLinkedBlockingQueue<W>> pool = new HashMap<K, VariableLinkedBlockingQueue<W>>();
    /** Those keys which want limits to be removed. We do not limit queue size if this is non-empty. */
    private final Set<K> unlimited = new HashSet<K>();

    /**
     * Add client <code><b>key</b></code> to pool of item queues, with an empty queue.
     * A client is initially <i>dormant</i>.
     * <p/>
     * No-op if <code><b>key</b></code> already present.
     * @param key client to add to pool
     */
    public void registerKey(K key) {
        synchronized (this) {
            if (!this.pool.containsKey(key)) {
                int initialCapacity = unlimited.isEmpty() ? MAX_QUEUE_LENGTH : Integer.MAX_VALUE;
                this.pool.put(key, new VariableLinkedBlockingQueue<W>(initialCapacity));
            }
        }
    }

    public synchronized void limit(K key) {
        unlimited.remove(key);
        if (unlimited.isEmpty()) {
            setCapacities(MAX_QUEUE_LENGTH);
        }
    }

    public synchronized void unlimit(K key) {
        unlimited.add(key);
        if (!unlimited.isEmpty()) {
            setCapacities(Integer.MAX_VALUE);
        }
    }

    private void setCapacities(int capacity) {
        Iterator<VariableLinkedBlockingQueue<W>> it = pool.values().iterator();
        while (it.hasNext()) {
            it.next().setCapacity(capacity);
        }
    }

    /**
     * Remove client from pool and from any other state. Has no effect if client already absent.
     * @param key of client to unregister
     */
    public void unregisterKey(K key) {
        synchronized (this) {
            this.pool.remove(key);
            this.ready.remove(key);
            this.inProgress.remove(key);
            this.unlimited.remove(key);
        }
    }

    /**
     * Remove all clients from pool and from any other state.
     */
    public void unregisterAllKeys() {
        synchronized (this) {
            this.pool.clear();
            this.ready.clear();
            this.inProgress.clear();
            this.unlimited.clear();
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
        synchronized (this) {
            K nextKey = readyToInProgress();
            if (nextKey != null) {
                VariableLinkedBlockingQueue<W> queue = this.pool.get(nextKey);
                drainTo(queue, to, size);
            }
            return nextKey;
        }
    }

    /**
     * Private implementation of <code><b>drainTo</b></code> (not implemented for <code><b>LinkedList&lt;W&gt;</b></code>s).
     * @param deList to take (poll) elements from
     * @param c to add elements to
     * @param maxElements to take from deList
     * @return number of elements actually taken
     */
    private int drainTo(VariableLinkedBlockingQueue<W> deList, Collection<W> c, int maxElements) {
        int n = 0;
        while (n < maxElements) {
            W first = deList.poll();
            if (first == null)
                break;
            c.add(first);
            ++n;
        }
        return n;
    }

    /**
     * Add (enqueue) an item for a specific client.
     * No change and returns <code><b>false</b></code> if client not registered.
     * If <i>dormant</i>, the client will be marked <i>ready</i>.
     * @param key the client to add to the work item to
     * @param item the work item to add to the client queue
     * @return <code><b>true</b></code> if and only if the client is marked <i>ready</i>
     * &mdash; <i>as a result of this work item</i>
     */
    public boolean addWorkItem(K key, W item) {
        VariableLinkedBlockingQueue<W> queue;
        synchronized (this) {
            queue = this.pool.get(key);
        }
        // The put operation may block. We need to make sure we are not holding the lock while that happens.
        if (queue != null) {
            try {
                queue.put(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (this) {
                if (isDormant(key)) {
                    dormantToReady(key);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Set client no longer <i>in progress</i>.
     * Ignore unknown clients (and return <code><b>false</b></code>).
     * @param key client that has finished work
     * @return <code><b>true</b></code> if and only if client becomes <i>ready</i>
     * @throws IllegalStateException if registered client not <i>in progress</i>
     */
    public boolean finishWorkBlock(K key) {
        synchronized (this) {
            if (!this.isRegistered(key))
                return false;
            if (!this.inProgress.contains(key)) {
                throw new IllegalStateException("Client " + key + " not in progress");
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
        VariableLinkedBlockingQueue<W> leList = this.pool.get(key);
        return leList != null && !leList.isEmpty();
    }

    /* State identification functions */
    private boolean isInProgress(K key){ return this.inProgress.contains(key); }
    private boolean isReady(K key){ return this.ready.contains(key); }
    private boolean isRegistered(K key) { return this.pool.containsKey(key); }
    private boolean isDormant(K key){ return !isInProgress(key) && !isReady(key) && isRegistered(key); }

    /* State transition methods - all assume key registered */
    private void inProgressToReady(K key){ this.inProgress.remove(key); this.ready.addIfNotPresent(key); }
    private void inProgressToDormant(K key){ this.inProgress.remove(key); }
    private void dormantToReady(K key){ this.ready.addIfNotPresent(key); }

    /* Basic work selector and state transition step */
    private K readyToInProgress() {
        K key = this.ready.poll();
        if (key != null) {
            this.inProgress.add(key);
        }
        return key;
    }
}
