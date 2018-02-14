// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for {@link WorkPool}
 */
public class WorkPoolTests {

    private final WorkPool<String, Object> pool = new WorkPool<String, Object>(-1);

    /**
     * Test unknown key tolerated silently
     * @throws Exception untested
     */
    @Test public void unknownKey() throws Exception{
        assertFalse(this.pool.addWorkItem("test", new Object()));
    }

    /**
     * Test add work and remove work
     * @throws Exception untested
     */
    @Test public void basicInOut() throws Exception {
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

    /**
     * Test add work when work in progress.
     * @throws Exception untested
     */
    @Test public void workInWhileInProgress() throws Exception {
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

    /**
     * Test multiple work keys.
     * @throws Exception untested
     */
    @Test public void interleavingKeys() throws Exception {
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

    /**
     * Test removal of key (with work)
     * @throws Exception untested
     */
    @Test public void unregisterKey() throws Exception {
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

    /**
     * Test removal of all keys (with work).
     * @throws Exception untested
     */
    @Test public void unregisterAllKeys() throws Exception {
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
