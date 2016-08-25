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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.ShutdownSignalException;

import junit.framework.AssertionFailedError;

public class ConfirmBase extends BrokerTestCase {
    protected void waitForConfirms() throws Exception
    {
        waitForConfirms("ConfirmBase.waitForConfirms");
    }

    protected void waitForConfirms(final String testTitle) throws Exception
    {
        try {
            FutureTask<?> waiter = new FutureTask<Object>(new Runnable() {
                    public void run() {
                        try {
                            channel.waitForConfirmsOrDie();
                        } catch (IOException e) {
                            throw (ShutdownSignalException)e.getCause();
                        } catch (InterruptedException _e) {
                            fail(testTitle + ": interrupted");
                        }
                    }
                }, null);
            (Executors.newSingleThreadExecutor()).execute(waiter);
            waiter.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException ee) {
            Throwable t = ee.getCause();
            if (t instanceof ShutdownSignalException) throw (ShutdownSignalException) t;
            if (t instanceof AssertionFailedError) throw (AssertionFailedError) t;
            throw (Exception)t;
        } catch (TimeoutException _e) {
            fail(testTitle + ": timeout");
        }
    }
}
