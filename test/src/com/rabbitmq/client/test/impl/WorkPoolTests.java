package com.rabbitmq.client.test.impl;

import com.rabbitmq.client.impl.WorkPool;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

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
        assertTrue(this.pool.workIn("test", one));
        assertFalse(this.pool.workIn("test", two));

        List<Runnable> workList = new ArrayList<Runnable>(16);
        String key = this.pool.nextBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(one, workList.get(0));

        assertTrue(this.pool.workBlockFinished(key));

        workList.clear();
        key = this.pool.nextBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(two, workList.get(0));

        assertFalse(this.pool.workBlockFinished(key));
    }

    public void testWorkInWhileInProgress() throws Exception {
        Runnable one = newRunnable();
        Runnable two = newRunnable();

        this.pool.registerKey("test");
        assertTrue(this.pool.workIn("test", one));


        List<Runnable> workList = new ArrayList<Runnable>(16);
        String key = this.pool.nextBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(one, workList.get(0));

        this.pool.workIn("test", two);

        this.pool.workBlockFinished(key);

        workList.clear();
        key = this.pool.nextBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(two, workList.get(0));
    }

    public void testInterleavingKeys() throws Exception {
        Runnable one = newRunnable();
        Runnable two = newRunnable();

        this.pool.registerKey("test1");
        this.pool.registerKey("test2");

        assertTrue(this.pool.workIn("test1", one));
        assertTrue(this.pool.workIn("test2", two));

        List<Runnable> workList = new ArrayList<Runnable>(16);
        String key = this.pool.nextBlock(workList, 1);
        assertEquals("test1", key);
        assertEquals(1, workList.size());
        assertEquals(one, workList.get(0));


        workList.clear();

        key = this.pool.nextBlock(workList, 1);
        assertEquals("test2", key);
        assertEquals(1, workList.size());
        assertEquals(two, workList.get(0));


    }

    private Runnable newRunnable() {
        return new Runnable() {
            public void run() {
            }
        };
    }


}
