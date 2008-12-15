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

import java.io.IOException;

import com.rabbitmq.client.test.functional.BrokerTestCase;

/**
 * Test for bug 20004 - deadlock through internal synchronization on
 * the channel object.
 */
public class Bug20004Test extends BrokerTestCase {
    public Exception caughtException = null;
    public boolean completed = false;
    public boolean created = false;

    protected void releaseResources()
        throws IOException
    {
        if (created) {
            channel.queueDelete("Bug20004Test");
        }
    }

    public void testBug20004()
        throws IOException
    {
        final Bug20004Test testInstance = this;

        Thread declaringThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        synchronized (channel) {
                            channel.queueDeclare("Bug20004Test");
                            testInstance.created = true;
                        }
                    } catch (Exception e) {
                        testInstance.caughtException = e;
                    }
                    testInstance.completed = true;
                }
            });
        declaringThread.start();

        long startTime = System.currentTimeMillis();
        while (!completed && (System.currentTimeMillis() - startTime < 5000)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {}
        }

        declaringThread.stop(); // see bug 20012.

        if (!completed) {
            fail("Deadlock detected. Probably.");
        }

        assertNull(caughtException);
        assertTrue(created);
    }
}
