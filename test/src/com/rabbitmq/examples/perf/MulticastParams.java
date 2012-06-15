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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A parameterisation of a single run, with creator methods for execution
 * <p/>
 * The setter methods on this class use a JavaBeans style
 * to permit text-driven update using introspection.
 * <p/>
 * Not all setters have getters, as the parameters are mostly consumed in the
 * creator methods (for {@link Producer}s and {@link Consumer}s).
 */
public class MulticastParams {
    private long confirm = -1; // no confirms
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

    /**
     * @param exchangeType exchange type for both producer and consumer
     */
    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    /**
     * @param exchangeName exchange name for both producer and consumer
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * @param queueName to consume from
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * @param rateLimit publishing rate limit (in msgs/sec), or zero for no limit
     */
    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }

    /**
     * @param producerCount number of producers
     */
    public void setProducerCount(int producerCount) {
        this.producerCount = producerCount;
    }

    /**
     * @param consumerCount number of consumers
     */
    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    /**
     * @param producerTxSize batching size (in messages published) for transactions,
     * or zero for no transactions
     */
    public void setProducerTxSize(int producerTxSize) {
        this.producerTxSize = producerTxSize;
    }

    /**
     * @param consumerTxSize batching size (in messages consumed) for transactions,
     * or zero for no transactions
     */
    public void setConsumerTxSize(int consumerTxSize) {
        this.consumerTxSize = consumerTxSize;
    }

    /**
     * @param confirm max number of publish confirms outstanding, or <=zero if no limit
     */
    public void setConfirm(long confirm) {
        this.confirm = confirm;
    }

    /**
     * @param autoAck consumer <code>autoAck</code> option
     */
    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    /**
     * @param multiAckEvery batching size for acks, or zero if acked individually,
     * ignored if autoAck is true
     */
    public void setMultiAckEvery(int multiAckEvery) {
        this.multiAckEvery = multiAckEvery;
    }

    /**
     * @param prefetchCount consumer channel setting: maximum number of messages
     * that the server will deliver on the consumer channel, 0 means unlimited
     */
    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * @param minMsgSize the smallest size (in bytes) a published message body can be
     */
    public void setMinMsgSize(int minMsgSize) {
        this.minMsgSize = minMsgSize;
    }

    /**
     * @param timeLimit limit of elapsed time to run in seconds, or zero if no limit
     */
    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    /**
     * @param producerMsgCount limit of number of messages produced, or zero if no limit
     */
    public void setProducerMsgCount(int producerMsgCount) {
        this.producerMsgCount = producerMsgCount;
    }

    /**
     * @param consumerMsgCount limit of number of messages consumed, or zero if no limit
     */
    public void setConsumerMsgCount(int consumerMsgCount) {
        this.consumerMsgCount = consumerMsgCount;
    }

    /**
     * @param msgCount sets both {@link #setProducerMsgCount} and {@link #setConsumerMsgCount}
     */
    public void setMsgCount(int msgCount) {
        setProducerMsgCount(msgCount);
        setConsumerMsgCount(msgCount);
    }

    /**
     * @param flags "mandatory", "immediate", "persistent" keyword list for {@link Producer}s;
     * all queues are declared durable flag if persistent flag is present.
     */
    public void setFlags(List<?> flags) {
        this.flags = flags;
    }

    /**
     * @param exclusive queues are declared exclusive if <code>true</code> (default), otherwise not
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    /**
     * @param autoDelete queues are declared auto-delete if <code>true</code>, otherwise not (default)
     */
    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    /**
     * @return the number of consumers
     */
    public int getConsumerCount() {
        return consumerCount;
    }

    /**
     * @return the number of producers
     */
    public int getProducerCount() {
        return producerCount;
    }

    /**
     * @return the smallest size (in bytes) a published message body is set to
     */
    public int getMinMsgSize() {
        return minMsgSize;
    }

    /**
     * Creates {@link Producer} designed for this parametrised run
     * @param channel producer publishes on
     * @param stats collector for timing statistics
     * @param id of producer/consumer routing key
     * @return producer (ready to run)
     * @throws IOException from channel ops
     */
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

    /**
     * Creates {@link Consumer} designed for this parametrised run
     * @param channel to consume from
     * @param stats collector for timing statistics
     * @param id of producer/consumer routing key
     * @return consumer (ready to run)
     * @throws IOException from channel ops
     */
    public Consumer createConsumer(Channel channel, Stats stats, String id) throws IOException {
        if (consumerTxSize > 0) channel.txSelect();
        channel.exchangeDeclare(exchangeName, exchangeType);
        String qName =
                channel.queueDeclare(queueName,
                                     flags.contains("persistent"),
                                     exclusive, autoDelete,
                                     null).getQueue();
        if (prefetchCount > 0) channel.basicQos(prefetchCount);
        channel.queueBind(qName, exchangeName, id);
        return new Consumer(channel, id, qName,
                                         consumerTxSize, autoAck, multiAckEvery,
                                         stats, consumerMsgCount, timeLimit);
    }

    /**
     * @return true if queue needs to be configured after run start
     */
    public boolean shouldConfigureQueue() {
        return consumerCount == 0 && !queueName.equals("");
    }

    /**
     * Configure (declare and bind) queue on channel
     * @param channel on which to configure queue
     * @param id routing key used to bind queue to exchange
     * @throws IOException declare and bind exceptions
     * @see #setQueueName(String)
     * @see #setExchangeName(String)
     */
    public void configureQueue(Channel channel, String id) throws IOException {
        channel.exchangeDeclare(exchangeName, exchangeType);
        channel.queueDeclare(queueName,
                             flags.contains("persistent"),
                             exclusive, autoDelete,
                             null).getQueue();
        channel.queueBind(queueName, exchangeName, id);
    }
}
