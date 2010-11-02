package com.rabbitmq.client.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author robharrop
 */
public class SetQueue<T> {
    private final Set<T> members = new HashSet<T>();
    private final BlockingQueue<T> queue = new LinkedBlockingQueue<T>();

    public boolean push(T item) {
        if (this.members.contains(item)) {
            return false;
        } else {
            this.members.add(item);
            this.queue.offer(item);
            return true;
        }

    }

    public T take() throws InterruptedException {
        // TODO: look at using drainTo
        T item =  this.queue.take();
        this.members.remove(item);
        return item;
    }
}
