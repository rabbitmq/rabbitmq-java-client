// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
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
 * Exception thrown when a published message is nack'd or returned by the broker.
 * <p>
 * This exception is thrown when publisher confirmation tracking is enabled and:
 * <ul>
 *   <li>The broker sends Basic.Nack for a message (negative acknowledgment)</li>
 *   <li>The broker returns a message via Basic.Return (unroutable with mandatory flag)</li>
 * </ul>
 * <p>
 * Use {@link #isReturn()} to distinguish between these two cases:
 * <ul>
 *   <li><b>Nack</b> ({@code isReturn() == false}): The broker rejected the message.
 *       Additional fields (exchange, routingKey, replyCode, replyText) will be null.</li>
 *   <li><b>Return</b> ({@code isReturn() == true}): The broker could not route the message.
 *       Additional fields contain routing information and the reason for the return.</li>
 * </ul>
 * <p>
 * <b>Example handling:</b>
 * <pre>{@code
 * ConfirmationChannel channel = ConfirmationChannel.create(regularChannel, rateLimiter);
 * channel.basicPublishAsync(exchange, routingKey, true, props, body, "msg-123")
 *     .exceptionally(ex -> {
 *         if (ex.getCause() instanceof PublishException) {
 *             PublishException pe = (PublishException) ex.getCause();
 *             String msgId = (String) pe.getContext();
 *             if (pe.isReturn()) {
 *                 System.err.println("Message " + msgId + " returned: " + pe.getReplyText());
 *             } else {
 *                 System.err.println("Message " + msgId + " nack'd");
 *             }
 *         }
 *         return null;
 *     });
 * }</pre>
 *
 * @see ConfirmationChannel#basicPublishAsync(String, String, com.rabbitmq.client.AMQP.BasicProperties, byte[], Object)
 * @see ConfirmationChannel
 */
public class PublishException extends IOException {
    private final long sequenceNumber;
    private final boolean isReturn;
    private final String exchange;
    private final String routingKey;
    private final Integer replyCode;
    private final String replyText;
    private final Object context;

    /**
     * Constructor for nack scenarios where routing details are not available.
     * <p>
     * When the broker sends Basic.Nack, it only provides the sequence number.
     * The exchange, routingKey, replyCode, and replyText fields will be null.
     *
     * @param sequenceNumber the publish sequence number
     * @param context the user-provided context object
     */
    public PublishException(long sequenceNumber, Object context)
    {
        this(sequenceNumber, false, null, null, null, null, context);
    }

    public PublishException(long sequenceNumber, boolean isReturn, String exchange, String routingKey,
                            Integer replyCode, String replyText, Object context) {
        super(buildMessage(sequenceNumber, isReturn, replyCode, replyText));
        this.sequenceNumber = sequenceNumber;
        this.isReturn = isReturn;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.replyCode = replyCode;
        this.replyText = replyText;
        this.context = context;
    }

    private static String buildMessage(long sequenceNumber, boolean isReturn, Integer replyCode, String replyText) {
        if (isReturn) {
            return String.format("Message %d returned: %s (%d)", sequenceNumber, replyText, replyCode);
        } else {
            return String.format("Message %d nack'd", sequenceNumber);
        }
    }

    /**
     * @return the publish sequence number of the failed message
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * @return true if this exception was caused by Basic.Return (unroutable message),
     *         false if caused by Basic.Nack (broker rejection)
     */
    public boolean isReturn() {
        return isReturn;
    }

    /**
     * @return the exchange the message was published to (only available for returns, null for nacks)
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * @return the routing key used (only available for returns, null for nacks)
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * @return the reply code from the broker (only available for returns, null for nacks)
     * @see com.rabbitmq.client.AMQP#NO_ROUTE
     * @see com.rabbitmq.client.AMQP#NO_CONSUMERS
     */
    public Integer getReplyCode() {
        return replyCode;
    }

    /**
     * @return the reply text from the broker explaining why the message was returned
     *         (only available for returns, null for nacks)
     */
    public String getReplyText() {
        return replyText;
    }

    /**
     * @return the user-provided context object that was passed to basicPublishAsync, or null if none was provided
     */
    public Object getContext() {
        return context;
    }
}
