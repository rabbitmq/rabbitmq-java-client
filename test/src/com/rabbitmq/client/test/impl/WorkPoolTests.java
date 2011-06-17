package com.rabbitmq.client.test.impl;

import com.rabbitmq.client.impl.WorkPool;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class WorkPoolTests extends TestCase {

    private WorkPool<String, Object> pool = new WorkPool<String, Object>();

    public void testUnkownKey() {
        try {
            this.pool.addWorkItem("test", new Object());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testBasicInOut() throws InterruptedException {
        Object one = new Object();
        Object two = new Object();

        this.pool.registerKey("test");
        assertTrue(this.pool.addWorkItem("test", one));
        assertFalse(this.pool.addWorkItem("test", two));

        List<Object> workList = new ArrayList<Object>(16);
        String key = this.pool.nextWorkBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(one, workList.get(0));

        assertTrue("Should be made ready", this.pool.finishWorkBlock(key));

        workList.clear();
        key = this.pool.nextWorkBlock(workList, 1);
        assertEquals("Work client key wrong", "test", key);
        assertEquals("Wrong work delivered", two, workList.get(0));

        assertFalse("Should not be made ready after this.", this.pool.finishWorkBlock(key));

        assertNull("Shouldn't be more work", this.pool.nextWorkBlock(workList, 1));
    }

    public void testWorkInWhileInProgress() throws Exception {
        Object one = new Object();
        Object two = new Object();

        this.pool.registerKey("test");
        assertTrue(this.pool.addWorkItem("test", one));

        List<Object> workList = new ArrayList<Object>(16);
        String key = this.pool.nextWorkBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(one, workList.get(0));

        assertFalse(this.pool.addWorkItem("test", two));

        assertTrue(this.pool.finishWorkBlock(key));

        workList.clear();
        key = this.pool.nextWorkBlock(workList, 1);
        assertEquals("test", key);
        assertEquals(1, workList.size());
        assertEquals(two, workList.get(0));
    }

    public void testInterleavingKeys() throws Exception {
        Object one = new Object();
        Object two = new Object();
        Object three = new Object();

        this.pool.registerKey("test1");
        this.pool.registerKey("test2");

        assertTrue(this.pool.addWorkItem("test1", one));
        assertTrue(this.pool.addWorkItem("test2", two));
        assertFalse(this.pool.addWorkItem("test1", three));

        List<Object> workList = new ArrayList<Object>(16);
        String key = this.pool.nextWorkBlock(workList, 3);
        assertEquals("test1", key);
        assertEquals(2, workList.size());
        assertEquals(one, workList.get(0));
        assertEquals(three, workList.get(1));

        workList.clear();

        key = this.pool.nextWorkBlock(workList, 2);
        assertEquals("test2", key);
        assertEquals(1, workList.size());
        assertEquals(two, workList.get(0));
    }

    public void testUnregisterKey() throws Exception {
        Object one = new Object();
        Object two = new Object();
        Object three = new Object();

        this.pool.registerKey("test1");
        this.pool.registerKey("test2");

        assertTrue(this.pool.addWorkItem("test1", one));
        assertTrue(this.pool.addWorkItem("test2", two));
        assertFalse(this.pool.addWorkItem("test1", three));

        this.pool.unregisterKey("test1");

        List<Object> workList = new ArrayList<Object>(16);
        String key = this.pool.nextWorkBlock(workList, 3);
        assertEquals("test2", key);
        assertEquals(1, workList.size());
        assertEquals(two, workList.get(0));
    }

    public void testUnregisterAllKeys() throws Exception {
        Object one = new Object();
        Object two = new Object();
        Object three = new Object();

        this.pool.registerKey("test1");
        this.pool.registerKey("test2");

        assertTrue(this.pool.addWorkItem("test1", one));
        assertTrue(this.pool.addWorkItem("test2", two));
        assertFalse(this.pool.addWorkItem("test1", three));

        this.pool.unregisterAllKeys();

        List<Object> workList = new ArrayList<Object>(16);
        assertNull(this.pool.nextWorkBlock(workList, 1));
    }
}
