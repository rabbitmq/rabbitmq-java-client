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
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * <code>Consumer</code> for tests based upon {@link QueueingConsumer}
 */
public class Consumer implements Runnable {

    private final Channel    channel;
    private final String     id;
    private final String     queueName;
    private final int        txSize;
    private final boolean    autoAck;
    private final int        multiAckEvery;
    private final Stats      stats;
    private final int        msgLimit;
    private final long       timeLimit;

    /**
     * Sole constructor
     * @param channel to consume from
     * @param id of producer/consumer routing key
     * @param queueName to consume from
     * @param txSize batching size (in messages consumed) for transactions, or zero for no transactions
     * @param autoAck consume <code>autoAck</code> option
     * @param multiAckEvery batching size for acks, or zero if acked individually, ignored if <code>autoAck</code> is <code>true</code>
     * @param stats collector for timing statistics
     * @param msgLimit limit of number of messages consumed, or zero if no limit
     * @param timeLimit limit of elapsed time to consume in seconds, or zero if no limit
     */
    public Consumer(Channel channel, String id,
                    String queueName, int txSize, boolean autoAck,
                    int multiAckEvery, Stats stats, int msgLimit, int timeLimit) {

        this.channel       = channel;
        this.id            = id;
        this.queueName     = queueName;
        this.txSize        = txSize;
        this.autoAck       = autoAck;
        this.multiAckEvery = multiAckEvery;
        this.stats         = stats;
        this.msgLimit      = msgLimit;
        this.timeLimit     = 1000L * timeLimit;
    }

    public void run() {
        QueueingConsumer q;
        long now;
        long startTime;
        startTime = now = System.currentTimeMillis();
        final long limitTime = startTime + timeLimit;
        int totalMsgCount = 0;

        try {
            q = new QueueingConsumer(channel);
            channel.basicConsume(queueName, autoAck, q);

            while ((timeLimit == 0 || now < limitTime) &&
                   (msgLimit == 0 || totalMsgCount < msgLimit)) {
                QueueingConsumer.Delivery delivery;
                try {
                    if (timeLimit == 0) {
                        delivery = q.nextDelivery();
                    } else {
                        delivery = q.nextDelivery(startTime + timeLimit - now);
                        if (delivery == null) break;
                    }
                } catch (ConsumerCancelledException e) {
                    System.out.println("Consumer cancelled by broker. Re-consuming.");
                    q = new QueueingConsumer(channel);
                    channel.basicConsume(queueName, autoAck, q);
                    continue;
                }
                totalMsgCount++;

                DataInputStream d = new DataInputStream(new ByteArrayInputStream(delivery.getBody()));
                d.readInt();
                long msgNano = d.readLong();
                long nano = System.nanoTime();

                Envelope envelope = delivery.getEnvelope();

                if (!autoAck) {
                    if (multiAckEvery == 0) {
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    } else if (totalMsgCount % multiAckEvery == 0) {
                        channel.basicAck(envelope.getDeliveryTag(), true);
                    }
                }

                if (txSize != 0 && totalMsgCount % txSize == 0) {
                    channel.txCommit();
                }

                now = System.currentTimeMillis();

                stats.handleRecv(id.equals(envelope.getRoutingKey()) ? (nano - msgNano) : 0L);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException (e);
        } catch (ShutdownSignalException e) {
            throw new RuntimeException(e);
        }
    }
}
