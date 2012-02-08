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

import java.util.ArrayList;
import java.util.List;

public class MulticastParams {
    protected long confirm = -1;
    protected int consumerCount = 1;
    protected int producerCount = 1;
    protected int consumerTxSize = 0;
    protected int producerTxSize = 0;
    protected int prefetchCount = 0;
    protected int minMsgSize = 0;

    protected int timeLimit = 10;
    protected int rateLimit = 0;
    protected int msgLimit = 0;

    protected String exchangeName = "direct";
    protected String exchangeType = "direct";
    protected String queueName = "";

    protected List<?> flags = new ArrayList<Object>();

    protected boolean autoAck = true;
    protected boolean exclusive = true;
    protected boolean autoDelete = false;

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

    public void setMsgLimit(int msgLimit) {
        this.msgLimit = msgLimit;
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
