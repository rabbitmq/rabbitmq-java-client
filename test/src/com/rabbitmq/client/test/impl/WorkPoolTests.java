package com.rabbitmq.client.test.impl;

import com.rabbitmq.client.impl.WorkPool;
import junit.framework.TestCase;

import java.util.List;
import java.util.LinkedList;

/**
 * @author robharrop
 */
public class WorkPoolTests extends TestCase {

    private WorkPool<String> pool = new WorkPool<String>();

    public void testUnkownKey() {
        try {
            this.pool.workIn("test", newRunnable());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testBasicInOut() throws InterruptedException {
        Runnable one = newRunnable();
        Runnable two = newRunnable();

        this.pool.registerKey("test");
        this.pool.workIn("test", one);
        this.pool.workIn("test", two);

        WorkPool.WorkBlock<String> workBlock = this.pool.nextBlock();
        assertEquals("test", workBlock.getKey());
        assertEquals(one, workBlock.getRunnable());

        this.pool.workBlockFinished(workBlock);

        workBlock = this.pool.nextBlock();
        assertEquals("test", workBlock.getKey());
        assertEquals(two, workBlock.getRunnable());
    }

    public void testWorkInWhileInProgress() throws Exception {
        Runnable one = newRunnable();
        Runnable two = newRunnable();

        this.pool.registerKey("test");
        this.pool.workIn("test", one);


        WorkPool.WorkBlock<String> workBlock = this.pool.nextBlock();
        assertEquals("test", workBlock.getKey());
        assertEquals(one, workBlock.getRunnable());

        this.pool.workIn("test", two);

        this.pool.workBlockFinished(workBlock);

        workBlock = this.pool.nextBlock();
        assertEquals("test", workBlock.getKey());
        assertEquals(two, workBlock.getRunnable());
    }

    private Runnable newRunnable() {
        return new Runnable() {
            public void run() {
            }
        };
    }

    private class WorkProducer implements Runnable {

        private final WorkPool<String> pool;

        private final String key;

        private WorkProducer(String key, WorkPool<String> pool) {
            this.pool = pool;
            this.key = key;
            this.pool.registerKey(key);
        }

        public void run() {
            for(int x = 0; x < 1000; x++) {
                pool.workIn(key, newRunnable());
            }
        }
    }

    private static class WorkBlockAccumulator implements Runnable {

        private final List<WorkPool.WorkBlock<String>> blocks =
                new LinkedList<WorkPool.WorkBlock<String>>();

        private final WorkPool<String> pool;

        private WorkBlockAccumulator(WorkPool<String> pool) {
            this.pool = pool;
        }

        public void run() {
            while(!Thread.interrupted()) {
                try {
                    WorkPool.WorkBlock<String> block = this.pool.nextBlock();
                    this.blocks.add(block);
                    this.pool.workBlockFinished(block);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public List<WorkPool.WorkBlock<String>> getBlocks() {
            synchronized (this.blocks) {
                return new LinkedList<WorkPool.WorkBlock<String>>(this.blocks);
            }
        }
    }
}
