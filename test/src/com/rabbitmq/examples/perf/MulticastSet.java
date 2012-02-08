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
//  Copyright (c) 2007-2012 VMware, Inc.  All rights reserved.
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
    private final MulticastParams p;

    public MulticastSet(Stats stats, ConnectionFactory factory,
                        MulticastParams params) {
        this.id = UUID.randomUUID().toString();
        this.stats = stats;
        this.factory = factory;
        this.p = params;
    }

    public void run() throws IOException, InterruptedException {
        run(false);
    }

    public void run(boolean announceStartup) throws IOException, InterruptedException {
        Thread[] consumerThreads = new Thread[p.consumerCount];
        Connection[] consumerConnections = new Connection[p.consumerCount];
        for (int i = 0; i < p.consumerCount; i++) {
            if (announceStartup) {
                System.out.println("starting consumer #" + i);
            }
            Connection conn = factory.newConnection();
            consumerConnections[i] = conn;
            Channel channel = conn.createChannel();
            if (p.consumerTxSize > 0) channel.txSelect();
            channel.exchangeDeclare(p.exchangeName, p.exchangeType);
            String qName =
                    channel.queueDeclare(p.queueName,
                                         p.flags.contains("persistent"),
                                         p.exclusive, p.autoDelete,
                                         null).getQueue();
            if (p.prefetchCount > 0) channel.basicQos(p.prefetchCount);
            channel.queueBind(qName, p.exchangeName, id);
            Thread t =
                new Thread(new Consumer(channel, id, qName,
                                        p.consumerTxSize, p.autoAck,
                                        stats, p.msgLimit, p.timeLimit));
            consumerThreads[i] = t;
        }

        if (p.consumerCount == 0 && !p.queueName.equals("")) {
            Connection conn = factory.newConnection();
            Channel channel = conn.createChannel();
            channel.queueDeclare(p.queueName,
                                 p.flags.contains("persistent"),
                                 p.exclusive, p.autoDelete,
                                 null).getQueue();
            channel.queueBind(p.queueName, p.exchangeName, id);
            conn.close();
        }

        Thread[] producerThreads = new Thread[p.producerCount];
        Connection[] producerConnections = new Connection[p.producerCount];
        Channel[] producerChannels = new Channel[p.producerCount];
        for (int i = 0; i < p.producerCount; i++) {
            if (announceStartup) {
                System.out.println("starting producer #" + i);
            }
            Connection conn = factory.newConnection();
            producerConnections[i] = conn;
            Channel channel = conn.createChannel();
            producerChannels[i] = channel;
            if (p.producerTxSize > 0) channel.txSelect();
            if (p.confirm >= 0) channel.confirmSelect();
            channel.exchangeDeclare(p.exchangeName, p.exchangeType);
            final Producer producer = new Producer(channel, p.exchangeName, id,
                                                   p.flags, p.producerTxSize,
                                                   p.rateLimit, p.msgLimit,
                                                   p.minMsgSize, p.timeLimit,
                                                   p.confirm, stats);
            channel.addReturnListener(producer);
            channel.addConfirmListener(producer);
            Thread t = new Thread(producer);
            producerThreads[i] = t;
        }

        for (int i = 0; i < p.consumerCount; i++) {
            consumerThreads[i].start();
        }

        for (int i = 0; i < p.producerCount; i++) {
            producerThreads[i].start();
        }

        for (int i = 0; i < p.producerCount; i++) {
            producerThreads[i].join();
            producerChannels[i].clearReturnListeners();
            producerChannels[i].clearConfirmListeners();
            producerConnections[i].close();
        }

        for (int i = 0; i < p.consumerCount; i++) {
            consumerThreads[i].join();
            consumerConnections[i].close();
        }
    }
}
