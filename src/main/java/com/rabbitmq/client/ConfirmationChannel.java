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

import com.rabbitmq.client.impl.ConfirmationChannelN;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A channel that supports asynchronous publisher confirmations.
 * <p>
 * This interface extends {@link Channel} to add the {@link #basicPublishAsync} method,
 * which returns a {@link CompletableFuture} that completes when the broker confirms
 * or rejects the published message.
 * <p>
 * Publisher confirmations are automatically enabled when using this channel type.
 * Messages are tracked using sequence numbers, and the future completes when:
 * <ul>
 *   <li>The broker sends Basic.Ack (successful confirmation)</li>
 *   <li>The broker sends Basic.Nack (rejection) - future completes exceptionally with {@link PublishException}</li>
 *   <li>The broker returns the message via Basic.Return (unroutable) - future completes exceptionally with {@link PublishException}</li>
 * </ul>
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * Channel channel = connection.createChannel();
 * ConfirmationChannel confirmChannel = ConfirmationChannel.create(channel, rateLimiter);
 *
 * confirmChannel.basicPublishAsync("exchange", "routing.key", props, body, "msg-123")
 *     .thenAccept(msgId -> System.out.println("Confirmed: " + msgId))
 *     .exceptionally(ex -> {
 *         if (ex.getCause() instanceof PublishException) {
 *             PublishException pe = (PublishException) ex.getCause();
 *             System.err.println("Failed: " + pe.getContext());
 *         }
 *         return null;
 *     });
 * }</pre>
 *
 * @see Channel
 * @see PublishException
 * @see com.rabbitmq.client.impl.ConfirmationChannelN
 */
public interface ConfirmationChannel extends Channel
{
    /**
     * Creates a new ConfirmationChannel by wrapping an existing channel.
     * <p>
     * This factory method enables asynchronous publisher confirmation tracking
     * on any existing channel. The wrapped channel will have publisher confirmations
     * automatically enabled via {@link Channel#confirmSelect()}.
     * <p>
     * <b>Example usage:</b>
     * <pre>{@code
     * Channel channel = connection.createChannel();
     * RateLimiter limiter = new ThrottlingRateLimiter(100, 50);
     * ConfirmationChannel confirmChannel = ConfirmationChannel.create(channel, limiter);
     *
     * confirmChannel.basicPublishAsync("exchange", "key", props, body, "msg-123")
     *     .thenAccept(msgId -> System.out.println("Confirmed: " + msgId));
     * }</pre>
     *
     * @param channel the channel to wrap
     * @param rateLimiter optional rate limiter for controlling publish concurrency (null for unlimited)
     * @return a new ConfirmationChannel instance
     * @throws IOException if enabling publisher confirmations fails
     * @see RateLimiter
     * @see ThrottlingRateLimiter
     */
    static ConfirmationChannel create(Channel channel, RateLimiter rateLimiter) throws IOException
    {
        return new ConfirmationChannelN(channel, rateLimiter);
    }

    /**
     * Asynchronously publish a message with publisher confirmation tracking.
     * <p>
     * This method publishes a message and returns a {@link CompletableFuture} that completes
     * when the broker confirms or rejects the message. The future's value is the context
     * parameter provided, allowing correlation between publish requests and confirmations.
     * <p>
     * The future completes:
     * <ul>
     *   <li><b>Successfully</b> with the context value when the broker sends Basic.Ack</li>
     *   <li><b>Exceptionally</b> with {@link PublishException} when:
     *     <ul>
     *       <li>The broker sends Basic.Nack (message rejected)</li>
     *       <li>The broker returns the message via Basic.Return (unroutable with mandatory flag)</li>
     *       <li>An I/O error occurs during publish</li>
     *       <li>The channel is closed before confirmation</li>
     *     </ul>
     *   </li>
     * </ul>
     * <p>
     * <b>Thread safety:</b> This method is thread-safe and can be called concurrently.
     * <p>
     * <b>Rate limiting:</b> If a {@link RateLimiter} is configured, this method will
     * block until a permit is available before publishing.
     *
     * @param exchange the exchange to publish to
     * @param routingKey the routing key
     * @param props message properties (null for default)
     * @param body message body
     * @param context user-provided context object for correlation (can be null)
     * @param <T> the type of the context parameter
     * @return a CompletableFuture that completes with the context value on success,
     *         or exceptionally with PublishException on failure
     * @throws IllegalStateException if the channel is closed
     */
    <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                                AMQP.BasicProperties props, byte[] body, T context);

    /**
     * Asynchronously publish a message with mandatory flag and publisher confirmation tracking.
     * <p>
     * This is equivalent to {@link #basicPublishAsync(String, String, AMQP.BasicProperties, byte[], Object)}
     * but allows specifying the mandatory flag. When mandatory is true, the broker will return
     * the message via Basic.Return if it cannot be routed to any queue, causing the future
     * to complete exceptionally with {@link PublishException}.
     *
     * @param exchange the exchange to publish to
     * @param routingKey the routing key
     * @param mandatory true if the message must be routable to at least one queue
     * @param props message properties (null for default)
     * @param body message body
     * @param context user-provided context object for correlation (can be null)
     * @param <T> the type of the context parameter
     * @return a CompletableFuture that completes with the context value on success,
     *         or exceptionally with PublishException on failure
     * @throws IllegalStateException if the channel is closed
     */
    <T> CompletableFuture<T> basicPublishAsync(String exchange, String routingKey,
                                                boolean mandatory,
                                                AMQP.BasicProperties props, byte[] body, T context);
}
