//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test;

import java.io.IOException;

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
    public void testBug20004() throws IOException
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
