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

package com.rabbitmq.examples.perf;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class MulticastSet {
    private final String id;
    private final Stats stats;
    private final ConnectionFactory factory;
    private final MulticastParams params;
    private final String testID;

    public MulticastSet(Stats stats, ConnectionFactory factory,
        MulticastParams params) {
        if (params.getRoutingKey() == null) {
            this.id = UUID.randomUUID().toString();
        } else {
            this.id = params.getRoutingKey();
        }
        this.stats = stats;
        this.factory = factory;
        this.params = params;
        this.testID = "perftest";
    }

    public MulticastSet(Stats stats, ConnectionFactory factory,
        MulticastParams params, String testID) {
        if (params.getRoutingKey() == null) {
            this.id = UUID.randomUUID().toString();
        } else {
            this.id = params.getRoutingKey();
        }
        this.stats = stats;
        this.factory = factory;
        this.params = params;
        this.testID = testID;
    }

    public void run() throws IOException, InterruptedException, TimeoutException {
        run(false);
    }

    public void run(boolean announceStartup) throws IOException, InterruptedException, TimeoutException {
        Thread[] consumerThreads = new Thread[params.getConsumerCount()];
        Connection[] consumerConnections = new Connection[consumerThreads.length];
        for (int i = 0; i < consumerConnections.length; i++) {
            if (announceStartup) {
                System.out.println("id: " + testID + ", starting consumer #" + i);
            }
            Connection conn = factory.newConnection();
            consumerConnections[i] = conn;
            Thread t = new Thread(params.createConsumer(conn, stats, id));
            consumerThreads[i] = t;
        }

        if (params.shouldConfigureQueue()) {
            Connection conn = factory.newConnection();
            params.configureQueue(conn, id);
            conn.close();
        }

        Thread[] producerThreads = new Thread[params.getProducerCount()];
        Connection[] producerConnections = new Connection[producerThreads.length];
        for (int i = 0; i < producerThreads.length; i++) {
            if (announceStartup) {
                System.out.println("id: " + testID + ", starting producer #" + i);
            }
            Connection conn = factory.newConnection();
            producerConnections[i] = conn;
            Thread t = new Thread(params.createProducer(conn, stats, id));
            producerThreads[i] = t;
        }

        for (Thread consumerThread : consumerThreads) {
            consumerThread.start();
        }

        for (Thread producerThread : producerThreads) {
            producerThread.start();
        }

        for (int i = 0; i < producerThreads.length; i++) {
            producerThreads[i].join();
            producerConnections[i].close();
        }

        for (int i = 0; i < consumerThreads.length; i++) {
            consumerThreads[i].join();
            consumerConnections[i].close();
        }
    }
}
