// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client;

import java.io.IOException;

/**
 * <p>Interface for application callback objects to receive notifications and messages from
 * a queue by subscription.
 * Most implementations will subclass {@link DefaultConsumer}.
 * </p>
 * <p>
 * The methods of this interface are invoked in a dispatch
 * thread which is separate from the {@link Connection}'s thread. This
 * allows {@link Consumer}s to call {@link Channel} or {@link
 * Connection} methods without causing a deadlock.
 * </p>
 * The {@link Consumer}s on a particular {@link Channel} are invoked serially on one or more
 * dispatch threads. {@link Consumer}s should avoid executing long-running code
 * because this will delay dispatch of messages to other {@link Consumer}s on the same
 * {@link Channel}.
 *
 * For a lambda-oriented syntax, use {@link DeliverCallback},
 * {@link CancelCallback}, and {@link ConsumerShutdownSignalCallback}.
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
