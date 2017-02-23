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


package com.rabbitmq.client.test;

import com.rabbitmq.utility.BlockingCell;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class BlockingCellTest
{

    @Test
    public void doubleSet() throws InterruptedException
    {
        BlockingCell<String> cell = new BlockingCell<String>();
        cell.set("one");
        assertEquals("one", cell.get());
        try {
            cell.set("two");
        } catch (IllegalStateException ae) {
            return;
        }
        fail("Expected IllegalStateException");
    }

    @Test public void multiGet()
        throws InterruptedException
    {
        final BlockingCell<String> cell = new BlockingCell<String>();
        cell.set("one");
        assertEquals("one", cell.get());
        assertEquals("one", cell.get());
    }

    @Test public void nullSet()
        throws InterruptedException
    {
        BlockingCell<Integer> c = new BlockingCell<Integer>();
        c.set(null);
        assertNull(c.get());
    }

    @Test public void earlySet()
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

    @Test public void lateSet()
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

    @Test public void getWaitsUntilSet() throws InterruptedException {
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

    @Test public void setIfUnset() throws InterruptedException {
        final BlockingCell<String> cell = new BlockingCell<String>();
        assertTrue(cell.setIfUnset("foo"));
        assertEquals("foo", cell.get());
        assertFalse(cell.setIfUnset("bar"));
        assertEquals("foo", cell.get());
    }
}
