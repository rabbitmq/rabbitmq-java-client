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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Consumer extends ProducerConsumerBase implements Runnable {

    private ConsumerImpl     q;
    private final Channel          channel;
    private final String           id;
    private final String           queueName;
    private final int              txSize;
    private final boolean          autoAck;
    private final int              multiAckEvery;
    private final Stats stats;
    private final int              msgLimit;
    private final long             timeLimit;
    private final CountDownLatch   latch = new CountDownLatch(1);

    public Consumer(Channel channel, String id,
                    String queueName, int txSize, boolean autoAck,
                    int multiAckEvery, Stats stats, float rateLimit, int msgLimit, int timeLimit) {

        this.channel       = channel;
        this.id            = id;
        this.queueName     = queueName;
        this.rateLimit     = rateLimit;
        this.txSize        = txSize;
        this.autoAck       = autoAck;
        this.multiAckEvery = multiAckEvery;
        this.stats         = stats;
        this.msgLimit      = msgLimit;
        this.timeLimit     = 1000L * timeLimit;
    }

    public void run() {
        try {
            q = new ConsumerImpl(channel);
            channel.basicConsume(queueName, autoAck, q);
            if (timeLimit == 0) {
                latch.await();
            }
            else {
                latch.await(timeLimit, TimeUnit.MILLISECONDS);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ShutdownSignalException e) {
            throw new RuntimeException(e);
        }
    }

    private class ConsumerImpl extends DefaultConsumer {
        long now;
        int totalMsgCount = 0;

        private ConsumerImpl(Channel channel) {
            super(channel);
            lastStatsTime = now = System.currentTimeMillis();
            msgCount = 0;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) throws IOException {
            totalMsgCount++;
            msgCount++;

            if (msgLimit == 0 || msgCount <= msgLimit) {
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(body));
                d.readInt();
                long msgNano = d.readLong();
                long nano = System.nanoTime();

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
                delay(now);
            }
            if (msgLimit != 0 && msgCount >= msgLimit) { // NB: not quite the inverse of above
                latch.countDown();
            }
        }

        @Override
        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
            latch.countDown();
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            System.out.println("Consumer cancelled by broker. Re-consuming.");
            channel.basicConsume(queueName, autoAck, q);
        }
    }
}
