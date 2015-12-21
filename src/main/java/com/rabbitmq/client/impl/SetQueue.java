package com.rabbitmq.client.impl;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/** A generic queue-like implementation (supporting operations <code>addIfNotPresent</code>,
 * <code>poll</code>, <code>contains</code>, and <code>isEmpty</code>)
 * which restricts a queue element to appear at most once.
 * If the element is already present {@link #addIfNotPresent} returns <code><b>false</b></code>.
 * <p/>
 * Elements must not be <code><b>null</b></code>.
 * <p/><b>Concurrent Semantics</b><br/>
 * This implementation is <i>not</i> thread-safe.
 * @param <T> type of elements in the queue
 */
public class SetQueue<T> {
    private final Set<T> members = new HashSet<T>();
    private final Queue<T> queue = new LinkedList<T>();

    /** Add an element to the back of the queue and return <code><b>true</b></code>, or else return <code><b>false</b></code>.
     * @param item to add
     * @return <b><code>true</code></b> if the element was added, <b><code>false</code></b> if it is already present.
     */
    public boolean addIfNotPresent(T item) {
        if (this.members.contains(item)) {
            return false;
        }
        this.members.add(item);
        this.queue.offer(item);
        return true;
    }

    /** Remove the head of the queue and return it.
     * @return head element of the queue, or <b><code>null</code></b> if the queue is empty.
     */
    public T poll() {
        T item =  this.queue.poll();
        if (item != null) {
            this.members.remove(item);
        }
        return item;
    }

    /** @param item to look for in queue
     * @return <code><b>true</b></code> if and only if <b>item</b> is in the queue.*/
    public boolean contains(T item) {
        return this.members.contains(item);
    }

    /** @return <code><b>true</b></code> if and only if the queue is empty.*/
    public boolean isEmpty() {
        return this.members.isEmpty();
    }

    /** Remove item from queue, if present.
     * @param item to remove
     *  @return <code><b>true</b></code> if and only if item was initially present and was removed.
     */
    public boolean remove(T item) {
        this.queue.remove(item); // there can only be one such item in the queue
        return this.members.remove(item);
    }

    /** Remove all items from the queue. */
    public void clear() {
        this.queue.clear();
        this.members.clear();
    }
}
