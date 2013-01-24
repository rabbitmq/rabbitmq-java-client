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

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.UUID;

public class MulticastSet {
    private final String id;
    private final Stats stats;
    private final ConnectionFactory factory;
    private final MulticastParams params;

    public MulticastSet(Stats stats, ConnectionFactory factory,
                        MulticastParams params) {
        this.id = UUID.randomUUID().toString();
        this.stats = stats;
        this.factory = factory;
        this.params = params;
    }

    public void run() throws IOException, InterruptedException {
        run(false);
    }

    public void run(boolean announceStartup) throws IOException, InterruptedException {
        Thread[] consumerThreads = new Thread[params.getConsumerCount()];
        Connection[] consumerConnections = new Connection[consumerThreads.length];
        for (int i = 0; i < consumerConnections.length; i++) {
            if (announceStartup) {
                System.out.println("starting consumer #" + i);
            }
            Connection conn = factory.newConnection();
            consumerConnections[i] = conn;
            Channel channel = conn.createChannel();
            Thread t = new Thread(params.createConsumer(channel, stats, id));
            consumerThreads[i] = t;
        }

        if (params.shouldConfigureQueue()) {
            Connection conn = factory.newConnection();
            Channel channel = conn.createChannel();
            params.configureQueue(channel, id);
            conn.close();
        }

        Thread[] producerThreads = new Thread[params.getProducerCount()];
        Connection[] producerConnections = new Connection[producerThreads.length];
        Channel[] producerChannels = new Channel[producerConnections.length];
        for (int i = 0; i < producerChannels.length; i++) {
            if (announceStartup) {
                System.out.println("starting producer #" + i);
            }
            Connection conn = factory.newConnection();
            producerConnections[i] = conn;
            Channel channel = conn.createChannel();
            producerChannels[i] = channel;
            Thread t = new Thread(params.createProducer(channel, stats, id));
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
            producerChannels[i].clearReturnListeners();
            producerChannels[i].clearConfirmListeners();
            producerConnections[i].close();
        }

        for (int i = 0; i < consumerThreads.length; i++) {
            consumerThreads[i].join();
            consumerConnections[i].close();
        }
    }
}
