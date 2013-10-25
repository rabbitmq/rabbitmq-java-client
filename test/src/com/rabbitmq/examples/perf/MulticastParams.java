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
//  Copyright (c) 2007-2013 GoPivotal, Inc.  All rights reserved.
//

package com.rabbitmq.examples.perf;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MulticastParams {
    private long confirm = -1;
    private int consumerCount = 1;
    private int producerCount = 1;
    private int consumerTxSize = 0;
    private int producerTxSize = 0;
    private int prefetchCount = 0;
    private int minMsgSize = 0;

    private int timeLimit = 0;
    private float rateLimit = 0;
    private int producerMsgCount = 0;
    private int consumerMsgCount = 0;

    private String exchangeName = "direct";
    private String exchangeType = "direct";
    private String queueName = "";
    private String routingKey = null;

    private List<?> flags = new ArrayList<Object>();

    private int multiAckEvery = 0;
    private boolean autoAck = true;
    private boolean exclusive = true;
    private boolean autoDelete = false;

    private boolean predeclared;

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public void setRateLimit(float rateLimit) {
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

    public void setMultiAckEvery(int multiAckEvery) {
        this.multiAckEvery = multiAckEvery;
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

    public void setProducerMsgCount(int producerMsgCount) {
        this.producerMsgCount = producerMsgCount;
    }

    public void setConsumerMsgCount(int consumerMsgCount) {
        this.consumerMsgCount = consumerMsgCount;
    }

    public void setMsgCount(int msgCount) {
        setProducerMsgCount(msgCount);
        setConsumerMsgCount(msgCount);
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

    public void setPredeclared(boolean predeclared) {
        this.predeclared = predeclared;
    }

    public int getConsumerCount() {
        return consumerCount;
    }

    public int getProducerCount() {
        return producerCount;
    }

    public int getMinMsgSize() {
        return minMsgSize;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public Producer createProducer(Connection connection, Stats stats, String id) throws IOException {
        Channel channel = connection.createChannel();
        if (producerTxSize > 0) channel.txSelect();
        if (confirm >= 0) channel.confirmSelect();
        if (!predeclared || !exchangeExists(connection, exchangeName)) {
            channel.exchangeDeclare(exchangeName, exchangeType);
        }
        final Producer producer = new Producer(channel, exchangeName, id,
                                               flags, producerTxSize,
                                               rateLimit, producerMsgCount,
                                               minMsgSize, timeLimit,
                                               confirm, stats);
        channel.addReturnListener(producer);
        channel.addConfirmListener(producer);
        return producer;
    }

    public Consumer createConsumer(Connection connection, Stats stats, String id) throws IOException {
        Channel channel = connection.createChannel();
        if (consumerTxSize > 0) channel.txSelect();
        String qName = configureQueue(connection, id);
        if (prefetchCount > 0) channel.basicQos(prefetchCount);
        return new Consumer(channel, id, qName,
                                         consumerTxSize, autoAck, multiAckEvery,
                                         stats, consumerMsgCount, timeLimit);
    }

    public boolean shouldConfigureQueue() {
        return consumerCount == 0 && !queueName.equals("") && !exclusive;
    }

    public String configureQueue(Connection connection, String id) throws IOException {
        Channel channel = connection.createChannel();
        if (!predeclared || !exchangeExists(connection, exchangeName)) {
            channel.exchangeDeclare(exchangeName, exchangeType);
        }
        String qName = queueName;
        if (!predeclared || !queueExists(connection, queueName)) {
            qName = channel.queueDeclare(queueName,
                                         flags.contains("persistent"),
                                         exclusive, autoDelete,
                                         null).getQueue();
        }
        channel.queueBind(qName, exchangeName, id);
        channel.close();
        return qName;
    }

    private static boolean exchangeExists(Connection connection, final String exchangeName) throws IOException {
        return exists(connection, new Checker() {
            public void check(Channel ch) throws IOException {
                ch.exchangeDeclarePassive(exchangeName);
            }
        });
    }

    private static boolean queueExists(Connection connection, final String queueName) throws IOException {
        return queueName != null && exists(connection, new Checker() {
            public void check(Channel ch) throws IOException {
                ch.queueDeclarePassive(queueName);
            }
        });
    }

    private static interface Checker {
        public void check(Channel ch) throws IOException;
    }

    private static boolean exists(Connection connection, Checker checker) throws IOException {
        try {
            Channel ch = connection.createChannel();
            checker.check(ch);
            ch.close();
            return true;
        }
        catch (IOException e) {
            ShutdownSignalException sse = (ShutdownSignalException) e.getCause();
            Command closeCommand = (Command) sse.getReason();
            if (!sse.isHardError()) {
                AMQP.Channel.Close closeMethod = (AMQP.Channel.Close) closeCommand.getMethod();
                if (closeMethod.getReplyCode() == AMQP.NOT_FOUND) {
                    return false;
                }
            }
            throw e;
        }
    }
}
