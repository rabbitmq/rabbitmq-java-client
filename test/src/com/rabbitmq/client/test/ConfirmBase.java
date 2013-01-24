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
//  Copyright (c) 2011-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test;

import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

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
                        } catch (InterruptedException _) {
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
        } catch (TimeoutException _) {
            fail(testTitle + ": timeout");
        }
    }
}
