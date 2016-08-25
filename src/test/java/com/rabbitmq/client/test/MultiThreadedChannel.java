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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests whether a Channel is safe for multi-threaded access
 */
public class MultiThreadedChannel extends BrokerTestCase {

    private static final String DUMMY_QUEUE_NAME = "dummy.queue";

    private static final String DUMMY_EXCHANGE_NAME = "dummy.exchange";

    @Test public void interleavedRpcs() throws Throwable {

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
