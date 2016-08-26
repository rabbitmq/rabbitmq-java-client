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

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test for bug 20004 - deadlock through internal synchronization on
 * the channel object. This is more properly a unit test, but since it
 * requires a connection to a broker, it's grouped with the functional
 * tests.
 * <p/>
 * Test calls channel.queueDeclare, while synchronising on channel, from
 * an independent thread.
 */
public class Bug20004Test extends BrokerTestCase {
    private volatile Exception caughtException = null;
    private volatile boolean completed = false;
    private volatile boolean created = false;

    protected void releaseResources()
        throws IOException
    {
        if (created) {
            channel.queueDelete("Bug20004Test");
        }
    }

    @SuppressWarnings("deprecation")
    @Test public void bug20004() throws IOException
    {
        final Bug20004Test testInstance = this;

        Thread declaringThread = new Thread(new Runnable() {
            public void run() {
                try {
                    synchronized (channel) {
                        channel.queueDeclare("Bug20004Test", false, false, false, null);
                        testInstance.created = true;
                    }
                } catch (Exception e) {
                    testInstance.caughtException = e;
                }
                testInstance.completed = true;
            }
        });
        declaringThread.start();

        // poll (100ms) for `completed`, up to 5s
        long endTime = System.currentTimeMillis() + 5000;
        while (!completed && (System.currentTimeMillis() < endTime)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {}
        }

        declaringThread.stop(); // see bug 20012.

        assertTrue("Deadlock detected?", completed);
        assertNull("queueDeclare threw an exception", caughtException);
        assertTrue("unknown sequence of events", created);
    }
}
