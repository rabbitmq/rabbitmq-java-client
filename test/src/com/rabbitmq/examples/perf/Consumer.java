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
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class Consumer implements Runnable {

    private QueueingConsumer q;
    private Channel channel;
    private String           id;
    private String           queueName;
    private int              txSize;
    private boolean          autoAck;
    private int              multiAckEvery;
    private Stats stats;
    private int              msgLimit;
    private long             timeLimit;

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
        long now;
        long startTime;
        startTime = now = System.currentTimeMillis();
        int totalMsgCount = 0;

        try {
            q = new QueueingConsumer(channel);
            channel.basicConsume(queueName, autoAck, q);

            while ((timeLimit == 0 || now < startTime + timeLimit) &&
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
