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

package com.rabbitmq.client;

import java.io.IOException;

/**
 * Interface for application callback objects to receive notifications and messages from
 * a queue by subscription.
 * Most implementations will subclass {@link DefaultConsumer}.
 * <p/>
 * The methods of this interface are invoked in a dispatch
 * thread which is separate from the {@link Connection}'s thread. This
 * allows {@link Consumer}s to call {@link Channel} or {@link
 * Connection} methods without causing a deadlock.
 * <p/>
 * The {@link Consumer}s on a particular {@link Channel} are invoked serially on one or more
 * dispatch threads. {@link Consumer}s should avoid executing long-running code
 * because this will delay dispatch of messages to other {@link Consumer}s on the same
 * {@link Channel}.
 *
 * @see Channel#basicConsume(String, boolean, String, boolean, boolean, java.util.Map, Consumer)
 * @see Channel#basicCancel
 */
public interface Consumer {
    /**
     * Called when the consumer is registered by a call to any of the
     * {@link Channel#basicConsume} methods.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    void handleConsumeOk(String consumerTag);

    /**
     * Called when the consumer is cancelled by a call to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    void handleCancelOk(String consumerTag);

    /**
     * Called when the consumer is cancelled for reasons <i>other than</i> by a call to
     * {@link Channel#basicCancel}. For example, the queue has been deleted.
     * See {@link #handleCancelOk} for notification of consumer
     * cancellation due to {@link Channel#basicCancel}.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @throws IOException
     */
    void handleCancel(String consumerTag) throws IOException;

    /**
     * Called when either the channel or the underlying connection has been shut down.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @param sig a {@link ShutdownSignalException} indicating the reason for the shut down
     */
    void handleShutdownSignal(String consumerTag, ShutdownSignalException sig);

    /**
     * Called when a <code><b>basic.recover-ok</b></code> is received
     * in reply to a <code><b>basic.recover</b></code>. All messages
     * received before this is invoked that haven't been <i>ack</i>'ed will be
     * re-delivered. All messages received afterwards won't be.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     */
    void handleRecoverOk(String consumerTag);

    /**
     * Called when a <code><b>basic.deliver</b></code> is received for this consumer.
     * @param consumerTag the <i>consumer tag</i> associated with the consumer
     * @param envelope packaging data for the message
     * @param properties content header data for the message
     * @param body the message body (opaque, client-specific byte array)
     * @throws IOException if the consumer encounters an I/O error while processing the message
     * @see Envelope
     */
    void handleDelivery(String consumerTag,
                        Envelope envelope,
                        AMQP.BasicProperties properties,
                        byte[] body)
        throws IOException;
}
