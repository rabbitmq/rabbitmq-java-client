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
    private int rateLimit = 0;
    private int producerMsgCount = 0;
    private int consumerMsgCount = 0;

    private String exchangeName = "direct";
    private String exchangeType = "direct";
    private String queueName = "";

    private List<?> flags = new ArrayList<Object>();

    private int multiAckEvery = 0;
    private boolean autoAck = true;
    private boolean exclusive = true;
    private boolean autoDelete = false;

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

    public int getConsumerCount() {
        return consumerCount;
    }

    public int getProducerCount() {
        return producerCount;
    }

    public int getMinMsgSize() {
        return minMsgSize;
    }

    public Producer createProducer(Channel channel, Stats stats, String id) throws IOException {
        if (producerTxSize > 0) channel.txSelect();
        if (confirm >= 0) channel.confirmSelect();
        channel.exchangeDeclare(exchangeName, exchangeType);
        final Producer producer = new Producer(channel, exchangeName, id,
                                               flags, producerTxSize,
                                               rateLimit, producerMsgCount,
                                               minMsgSize, timeLimit,
                                               confirm, stats);
        channel.addReturnListener(producer);
        channel.addConfirmListener(producer);
        return producer;
    }

    public Consumer createConsumer(Channel channel, Stats stats, String id) throws IOException {
        if (consumerTxSize > 0) channel.txSelect();
        String qName = configureQueue(channel, id);
        if (prefetchCount > 0) channel.basicQos(prefetchCount);
        return new Consumer(channel, id, qName,
                                         consumerTxSize, autoAck, multiAckEvery,
                                         stats, consumerMsgCount, timeLimit);
    }

    public boolean shouldConfigureQueue() {
        return consumerCount == 0 && !queueName.equals("");
    }

    public String configureQueue(Channel channel, String id) throws IOException {
        channel.exchangeDeclare(exchangeName, exchangeType);
        String qName = channel.queueDeclare(queueName,
                                            flags.contains("persistent"),
                                            exclusive, autoDelete,
                                            null).getQueue();
        channel.queueBind(qName, exchangeName, id);
        return qName;
    }
}
