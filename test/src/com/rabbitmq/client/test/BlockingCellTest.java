//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.utility.BlockingCell;

public class BlockingCellTest
    extends TestCase
{
    public static TestSuite suite()
    {
        TestSuite suite = new TestSuite("blockingCells");
        suite.addTestSuite(BlockingCellTest.class);
        return suite;
    }

    public void testDoubleSet() throws InterruptedException
    {
        BlockingCell<String> cell = new BlockingCell<String>();
        cell.set("one");
        assertEquals("one", cell.get());
        try {
            cell.set("two");
        } catch (AssertionError ae) {
            return;
        }
        fail("Expected AssertionError");
    }

    public void testMultiGet()
        throws InterruptedException
    {
        final BlockingCell<String> cell = new BlockingCell<String>();
        cell.set("one");
        assertEquals("one", cell.get());
        assertEquals("one", cell.get());
    }

    public void testNullSet()
        throws InterruptedException
    {
        BlockingCell<Integer> c = new BlockingCell<Integer>();
        c.set(null);
        assertNull(c.get());
    }

    public void testEarlySet()
        throws InterruptedException
    {
        final BlockingCell<String> cell = new BlockingCell<String>();
        final AtomicReference<String> holder = new AtomicReference<String>();

        Thread getterThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(300);
                        holder.set(cell.get());
                    } catch (InterruptedException ie) {
                        // no special handling required
                    }
                }
            });
        Thread setterThread = new Thread(new Runnable() {
                public void run() {
                    cell.set("hello");
                }
            });

        getterThread.start();
        setterThread.start();

        getterThread.join();
        setterThread.join();

        assertEquals("hello", holder.get());
    }

    public void testLateSet()
        throws InterruptedException
    {
        final BlockingCell<String> cell = new BlockingCell<String>();
        final AtomicReference<String> holder = new AtomicReference<String>();

        Thread getterThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        holder.set(cell.get());
                    } catch (InterruptedException ie) {
                        // no special handling required
                    }
                }
            });
        Thread setterThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(300);
                        cell.set("hello");
                    } catch (InterruptedException ie) {
                        // no special handling required
                    }
                }
            });

        getterThread.start();
        setterThread.start();

        getterThread.join();
        setterThread.join();

        assertEquals("hello", holder.get());
    }
    
    public void testGetWaitsUntilSet() throws InterruptedException {
        final BlockingCell<String> cell = new BlockingCell<String>();
        final String value = "foo";
        final AtomicReference<Object> valueHolder = new AtomicReference<Object>();
        final AtomicBoolean doneHolder = new AtomicBoolean(false);
        Thread getterThread = new Thread() {
            @Override public void run() {
                try {
                    valueHolder.set(cell.get());
                } catch (InterruptedException ex) {
                    fail("hit InterruptedException");
                    ex.printStackTrace();
                }
                doneHolder.set(true);
            }
        };
        getterThread.start();
        Thread.sleep(300);
        assertFalse(doneHolder.get());
        cell.set(value);
        getterThread.join();
        assertTrue(doneHolder.get());
        assertTrue(value == valueHolder.get());
    }

    public void testSetIfUnset() throws InterruptedException {
        final BlockingCell<String> cell = new BlockingCell<String>();
        assertTrue(cell.setIfUnset("foo"));
        assertEquals("foo", cell.get());
        assertFalse(cell.setIfUnset("bar"));
        assertEquals("foo", cell.get());
    }
}
