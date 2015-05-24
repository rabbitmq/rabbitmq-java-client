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
//  The Initial Developer of the Original Code is GoPivotal, Inc.
//  Copyright (c) 2007-2015 Pivotal Software, Inc.  All rights reserved.
//

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class MulticastSet {
    private final String id;
    private final Stats stats;
    private final ConnectionFactory factory;
    private final MulticastParams params;

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
    }

    public void run() throws IOException, InterruptedException, TimeoutException {
        run(false);
    }

    public void run(boolean announceStartup) throws IOException, InterruptedException, TimeoutException {
        Thread[] consumerThreads = new Thread[params.getConsumerCount()];
        Connection[] consumerConnections = new Connection[consumerThreads.length];
        for (int i = 0; i < consumerConnections.length; i++) {
            if (announceStartup) {
                System.out.println("starting consumer #" + i);
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
                System.out.println("starting producer #" + i);
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
