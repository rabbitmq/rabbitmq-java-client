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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ReturnListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

/**
 * <code>Producer</code> for tests
 */
public class Producer implements Runnable, ReturnListener,
        ConfirmListener
{
    private final Channel channel;
    private final String  exchangeName;
    private final String  id;
    private final boolean mandatory;
    private final boolean immediate;
    private final boolean persistent;
    private final int     txSize;
    private final int     rateLimit;
    private final int     msgLimit;
    private final long    timeLimit;

    private final Stats   stats;

    private final byte[]  message;

    private long    startTime;
    private long    lastStatsTime;
    private int     msgCount;

    private final Semaphore confirmPool;
    private volatile SortedSet<Long> unconfirmedSet =
        Collections.synchronizedSortedSet(new TreeSet<Long>());

    /**
     * Ctor
     * @param channel producer publishes on
     * @param exchangeName to publish to
     * @param id of producer/consumer routing key
     * @param flags "mandatory", "immediate", "persistent" keyword list
     * @param txSize batching size (in messages published) for transactions, or zero for no transactions
     * @param rateLimit publishing rate limit (in msgs/sec), or zero for no limit
     * @param msgLimit max number of messages to publish, or zero for no limit
     * @param minMsgSize smallest size a message body can be
     * @param timeLimit limit of elapsed time to publish in seconds, or zero if no limit
     * @param confirm max number of confirms outstanding, or zero if no limit
     * @param stats collector for timing statistics
     */
    public Producer(Channel channel, String exchangeName, String id,
                    List<?> flags, int txSize,
                    int rateLimit, int msgLimit, int minMsgSize, int timeLimit,
                    long confirm, Stats stats)
    {

        this.channel      = channel;
        this.exchangeName = exchangeName;
        this.id           = id;
        this.mandatory    = flags.contains("mandatory");
        this.immediate    = flags.contains("immediate");
        this.persistent   = flags.contains("persistent");
        this.txSize       = txSize;
        this.rateLimit    = rateLimit;
        this.msgLimit     = msgLimit;
        this.timeLimit    = 1000L * timeLimit;
        this.message      = new byte[minMsgSize];
        this.confirmPool  = (confirm == 0) ? null : new Semaphore((int)confirm);
        this.stats        = stats;
    }

    public void handleReturn(int replyCode,
                             String replyText,
                             String exchange,
                             String routingKey,
                             AMQP.BasicProperties properties,
                             byte[] body)
        throws IOException {
        stats.handleReturn();
    }

    public void handleAck(long seqNo, boolean multiple) {
        handleAckNack(seqNo, multiple, false);
    }

    public void handleNack(long seqNo, boolean multiple) {
        handleAckNack(seqNo, multiple, true);
    }

    private void handleAckNack(long seqNo, boolean multiple,
                               boolean nack) {
        int numConfirms = 0;
        if (multiple) {
            SortedSet<Long> confirmed = unconfirmedSet.headSet(seqNo + 1);
            numConfirms += confirmed.size();
            confirmed.clear();
        } else {
            unconfirmedSet.remove(seqNo);
            numConfirms = 1;
        }
        if (nack) {
            stats.handleNack(numConfirms);
        } else {
            stats.handleConfirm(numConfirms);
        }

        if (confirmPool != null) {
            for (int i = 0; i < numConfirms; ++i) {
                confirmPool.release();
            }
        }

    }

    public void run() {
        long now = startTime = lastStatsTime = System.currentTimeMillis();
        final long limitTime = startTime + timeLimit;
        msgCount = 0;
        int totalMsgCount = 0;

        try {

            while ((timeLimit == 0 || now < limitTime) &&
                   (msgLimit == 0 || msgCount < msgLimit)) {
                delay(now);
                if (confirmPool != null) {
                    confirmPool.acquire();
                }
                publish(createMessage(totalMsgCount));
                totalMsgCount++;
                msgCount++;

                if (txSize != 0 && totalMsgCount % txSize == 0) {
                    channel.txCommit();
                }
                stats.handleSend();
                now = System.currentTimeMillis();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException (e);
        }
    }

    private void publish(byte[] msg)
        throws IOException {

        unconfirmedSet.add(channel.getNextPublishSeqNo());
        channel.basicPublish(exchangeName, id,
                             mandatory, immediate,
                             persistent ? MessageProperties.MINIMAL_PERSISTENT_BASIC : MessageProperties.MINIMAL_BASIC,
                             msg);
    }

    private void delay(long now)
        throws InterruptedException {

        long elapsed = now - lastStatsTime;
        //example: rateLimit is 5000 msg/s,
        //10 ms have elapsed, we have sent 200 messages
        //the 200 msgs we have actually sent should have taken us
        //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
        long pause = rateLimit == 0 ?
            0 : (msgCount * 1000L / rateLimit - elapsed);
        if (pause > 0) {
            Thread.sleep(pause);
        }
    }

    private byte[] createMessage(int sequenceNumber)
        throws IOException {

        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(acc);
        long nano = System.nanoTime();
        d.writeInt(sequenceNumber);
        d.writeLong(nano);
        d.flush();
        acc.flush();
        byte[] m = acc.toByteArray();
        if (m.length <= message.length) {
            System.arraycopy(m, 0, message, 0, m.length);
            return message;
        } else {
            return m;
        }
    }

}
