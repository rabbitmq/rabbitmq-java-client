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
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.impl.AMQImpl.Tx;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.io.IOException;

/**
 * An extension to {@link ChannelN} that simplifies the very common use-case of publishing a lot of messages and waiting for them all to be confirmed.
 * <p>
 * To open a channel,
 * <pre>
 * {@link Connection} conn = ...;
 * {@link ConfirmChannel} ch = conn.{@link Connection#createChannel createConfirmChannel}();
 * </pre>
 */

public class ConfirmChannelN extends ChannelN implements com.rabbitmq.client.ConfirmChannel {
    public volatile ConfirmListener chainedConfirmListener;
    private volatile SortedSet<Long> unconfirmedSet =
            Collections.synchronizedSortedSet(new TreeSet<Long>());
    private boolean nacksReceived = false;

    /** {@inheritDoc} */
    public ConfirmChannelN(AMQConnection connection, int channelNumber)
        throws IOException
    {
        super(connection, channelNumber);

        setConfirmListener(new ConfirmListener() {
                public void handleAck(long seqNo, boolean multiple)
                    throws IOException
                {
                    handleAckNack(seqNo, multiple, false);
                    if (chainedConfirmListener != null)
                        chainedConfirmListener.handleAck(seqNo, multiple);
                }

                public void handleNack(long seqNo, boolean multiple)
                    throws IOException
                {
                    handleAckNack(seqNo, multiple, true);
                    if (chainedConfirmListener != null)
                        chainedConfirmListener.handleAck(seqNo, multiple);
                }
            });
        confirmSelect();
    }

    /** {@inheritDoc} */
    public void basicPublish(String exchange, String routingKey,
                             boolean mandatory, boolean immediate,
                             BasicProperties props, byte[] body)
        throws IOException
    {
        unconfirmedSet.add(getNextPublishSeqNo());
        super.basicPublish(exchange, routingKey, mandatory, immediate, props, body);
    }

    /** {@inheritDoc} */
    public void setConfirmListener(ConfirmListener listener) {
        chainedConfirmListener = listener;
    }

    /** {@inheritDoc} */
    public ConfirmListener getConfirmListener() {
        return chainedConfirmListener;
    }

    /** This method is not supported by ConfirmChannelN
        @throws UnsupportedOperationException
    */
    public Tx.SelectOk txSelect()
        throws IOException
    {
        throw new UnsupportedOperationException("tx methods not supported on ConfirmChannelN");
    }

    /** This method is not supported by ConfirmChannelN
        @throws UnsupportedOperationException
    */
    public Tx.CommitOk txCommit()
        throws IOException
    {
        throw new UnsupportedOperationException("tx methods not supported on ConfirmChannelN");
    }

    /** This method is not supported by ConfirmChannelN
        @throws UnsupportedOperationException
    */
    public Tx.RollbackOk txRollback()
        throws IOException
    {
        throw new UnsupportedOperationException("tx methods not supported on ConfirmChannelN");
    }

    /** {@inheritDoc} */
    public boolean waitForConfirms()
        throws InterruptedException
    {
        synchronized (this) {
            while (unconfirmedSet.size() > 0)
                wait();
        }

        boolean noNacksReceived = !nacksReceived;
        nacksReceived = false;
        return noNacksReceived;
    }

    private void handleAckNack(long seqNo, boolean multiple, boolean nack) {
        int numConfirms = 0;
        if (multiple) {
            SortedSet<Long> confirmed = unconfirmedSet.headSet(seqNo + 1);
            numConfirms += confirmed.size();
            confirmed.clear();
        } else {
            unconfirmedSet.remove(seqNo);
            numConfirms = 1;
        }
        synchronized (this) {
            nacksReceived = nacksReceived || nack;
            notify();
        }
    }
}
