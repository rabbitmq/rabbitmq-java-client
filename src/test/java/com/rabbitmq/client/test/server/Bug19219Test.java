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

package com.rabbitmq.client.test.server;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.test.BrokerTestCase;

/**
 * Test for bug 19219 - timeouts due to task parallelism in channel
 * closure code.
 */
public class Bug19219Test extends BrokerTestCase {

    /*
      These settings require careful tuning. Depending on them we get
      one of three outcomes:
      1) this program terminates normally
      2) the broker runs out of memory and eventually dies, and the
      this program barfs and/or dies
      3) there are lots of timeout errors in the broker log

      It's the last case we are interested in.

      The settings below work on tanto against default.
    */
    private static final int Q_COUNT = 1500;
    private static final int PUB_THREAD_COUNT = 100;
    private static final int CLOSE_DELAY = 2000;

    private static final Semaphore init = new Semaphore(0);
    private static final CountDownLatch resume = new CountDownLatch(1);

    private static void publish(final Channel ch)
        throws IOException {
        ch.basicPublish("amq.fanout", "",
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        new byte[0]);
    }

    @Test public void it() throws IOException, InterruptedException {

        final Consumer c = new DefaultConsumer(channel);

        //1. create lots of auto-delete queues, bind them to the
        //amq.fanout exchange, and set up a non-auto-ack consumer for
        //each.
        for (int i = 0; i < Q_COUNT; i++) {
            String qName = channel.queueDeclare().getQueue();
            channel.queueBind(qName, "amq.fanout", "");
            channel.basicConsume(qName, false, c);
        }

        //2. send lots of messages in background, to keep the server,
        //and especially the queues, busy
        final Runnable r = new Runnable() {
                public void run() {
                    try {
                        startPublisher();
                    } catch (IOException e) {
                    } catch (InterruptedException e) {
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                    }
                }
            };

        for (int i = 0; i < PUB_THREAD_COUNT; i++) {
            final Thread t = new Thread(r);
            t.start();
            //wait for thread to finish initialisation
            init.acquire();
        }

        //tell all threads to resume
        resume.countDown();

        //wait for threads to get into full swing
        Thread.sleep(CLOSE_DELAY);

        //3. close channel. This will result in the server notifying
        //all the queues in parallel, which in turn will requeue all
        //the messages. The combined workload may result in some
        //notifications timing out.
        boolean success = false;
        try {
            channel.abort();
            success = true;
        } catch (ShutdownSignalException e) {
        } finally {
            //We deliberately do not perform a clean shutdown of all
            //the connections. This test is pushing the server really
            //hard, so we chose the quickest way to end things.
            channel = null;
            connection = null;

            assertTrue(success);
        }
    }

    private void startPublisher() throws IOException, InterruptedException, TimeoutException {

        final Connection conn = connectionFactory.newConnection();
        final Channel pubCh = conn.createChannel();

        //This forces the initialisation of the guid generation, which
        //is an interaction with the persister and not something we
        //want to see delay things.
        publish(pubCh);

        //a synchronous request, to make sure the publish is done
        pubCh.queueDeclare();

        //signal the main thread
        init.release();
        //wait for main thread to let us resume
        resume.await();

        //publish lots of messages
        while(true) {
            publish(pubCh);
        }

    }

}
