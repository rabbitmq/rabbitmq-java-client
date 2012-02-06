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
import java.util.List;

public class ProducerConsumerSet {
    private String exchangeType;
    private String exchangeName;
    private String queueName;
    private int rateLimit;
    private int producerCount;
    private int consumerCount;
    private int producerTxSize;
    private int consumerTxSize;
    private long confirm;
    private boolean autoAck;
    private int prefetchCount;
    private int minMsgSize;
    private int timeLimit;
    private List<?> flags;
    private boolean exclusive;
    private boolean autoDelete;
    private final String id;
    private final Stats stats;
    private final ConnectionFactory factory;

    public ProducerConsumerSet(String id, Stats stats, ConnectionFactory factory) {
        this.id = id;
        this.stats = stats;
        this.factory = factory;
    }

    public void run() throws IOException, InterruptedException {
        Thread[] consumerThreads = new Thread[consumerCount];
        Connection[] consumerConnections = new Connection[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            System.out.println("starting consumer #" + i);
            Connection conn = factory.newConnection();
            consumerConnections[i] = conn;
            Channel channel = conn.createChannel();
            if (consumerTxSize > 0) channel.txSelect();
            channel.exchangeDeclare(exchangeName, exchangeType);
            String qName =
                    channel.queueDeclare(queueName,
                                         flags.contains("persistent"),
                                         exclusive, autoDelete,
                                         null).getQueue();
            if (prefetchCount > 0) channel.basicQos(prefetchCount);
            channel.queueBind(qName, exchangeName, id);
            Thread t =
                new Thread(new Consumer(channel, id, qName,
                                        consumerTxSize, autoAck,
                                        stats, timeLimit));
            consumerThreads[i] = t;
        }

        Thread[] producerThreads = new Thread[producerCount];
        Connection[] producerConnections = new Connection[producerCount];
        Channel[] producerChannels = new Channel[producerCount];
        for (int i = 0; i < producerCount; i++) {
            System.out.println("starting producer #" + i);
            Connection conn = factory.newConnection();
            producerConnections[i] = conn;
            Channel channel = conn.createChannel();
            producerChannels[i] = channel;
            if (producerTxSize > 0) channel.txSelect();
            if (confirm >= 0) channel.confirmSelect();
            channel.exchangeDeclare(exchangeName, exchangeType);
            final Producer p = new Producer(channel, exchangeName, id,
                                            flags, producerTxSize,
                                            rateLimit, minMsgSize, timeLimit,
                                            confirm, stats);
            channel.addReturnListener(p);
            channel.addConfirmListener(p);
            Thread t = new Thread(p);
            producerThreads[i] = t;
        }

        for (int i = 0; i < consumerCount; i++) {
            consumerThreads[i].start();
        }

        for (int i = 0; i < producerCount; i++) {
            producerThreads[i].start();
        }

        for (int i = 0; i < producerCount; i++) {
            producerThreads[i].join();
            producerChannels[i].clearReturnListeners();
            producerChannels[i].clearConfirmListeners();
            producerConnections[i].close();
        }

        for (int i = 0; i < consumerCount; i++) {
            consumerThreads[i].join();
            consumerConnections[i].close();
        }
    }

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }

    public void setProducerCount(int producerCount) {
        this.producerCount = producerCount;
    }

    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    public void setProducerTxSize(int producerTxSize) {
        this.producerTxSize = producerTxSize;
    }

    public void setConsumerTxSize(int consumerTxSize) {
        this.consumerTxSize = consumerTxSize;
    }

    public void setConfirm(long confirm) {
        this.confirm = confirm;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public void setMinMsgSize(int minMsgSize) {
        this.minMsgSize = minMsgSize;
    }

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public void setFlags(List<?> flags) {
        this.flags = flags;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }
}
