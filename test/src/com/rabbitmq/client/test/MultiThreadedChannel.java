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

import com.rabbitmq.client.test.BrokerTestCase;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests whether a Channel is safe for multi-threaded access
 */
public class MultiThreadedChannel extends BrokerTestCase {

    private static final String DUMMY_QUEUE_NAME = "dummy.queue";

    private static final String DUMMY_EXCHANGE_NAME = "dummy.exchange";

    public void testInterleavedRpcs() throws Throwable {

        final AtomicReference<Throwable> throwableRef = new AtomicReference<Throwable>(null);

        Thread queueDeclare = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int x = 0; x < 100; x++) {
                        channel.queueDeclare(DUMMY_QUEUE_NAME, false, false, true, null);
                    }
                } catch (Throwable e) {
                    throwableRef.set(e);
                }
            }
        });

        Thread exchangeDeclare = new Thread(new Runnable() {
            public void run() {

                try {
                    for (int x = 0; x < 100; x++) {
                        channel.exchangeDeclare(DUMMY_EXCHANGE_NAME, "direct");
                    }
                } catch (Throwable e) {
                    throwableRef.set(e);
                }
            }
        });

        queueDeclare.start();
        exchangeDeclare.start();
        queueDeclare.join();
        exchangeDeclare.join();

        Throwable t = throwableRef.get();
        if (t != null) {
            throw t;
        }
    }
}
