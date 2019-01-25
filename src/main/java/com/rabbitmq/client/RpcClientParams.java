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

import java.util.function.Function;

/**
 * Holder class to configure a {@link RpcClient}.
 *
 * @see RpcClient#RpcClient(RpcClientParams)
 * @since 5.6.0
 */
public class RpcClientParams {

    /**
     * Channel we are communicating on
     */
    private Channel channel;
    /**
     * Exchange to send requests to
     */
    private String exchange;
    /**
     * Routing key to use for requests
     */
    private String routingKey;
    /**
     * Queue where the server should put the reply
     */
    private String replyTo = "amq.rabbitmq.reply-to";
    /**
     * Timeout in milliseconds to use on call responses
     */
    private int timeout = RpcClient.NO_TIMEOUT;
    /**
     * Whether to publish RPC requests with the mandatory flag or not.
     */
    private boolean useMandatory = false;
    /**
     * Behavior to handle reply messages.
     */
    private Function<Object, RpcClient.Response> replyHandler = RpcClient.DEFAULT_REPLY_HANDLER;

    /**
     * Set the channel to use for communication.
     *
     * @return
     */
    public Channel getChannel() {
        return channel;
    }

    public RpcClientParams channel(Channel channel) {
        this.channel = channel;
        return this;
    }

    /**
     * Set the exchange to send requests to.
     *
     * @return
     */
    public String getExchange() {
        return exchange;
    }

    public RpcClientParams exchange(String exchange) {
        this.exchange = exchange;
        return this;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * Set the routing key to use for requests.
     *
     * @param routingKey
     * @return
     */
    public RpcClientParams routingKey(String routingKey) {
        this.routingKey = routingKey;
        return this;
    }

    public String getReplyTo() {
        return replyTo;
    }

    /**
     * Set the queue where the server should put replies on.
     * <p>
     * The default is to use
     * <a href="https://www.rabbitmq.com/direct-reply-to.html">Direct Reply-to</a>.
     * Using another value will cause the creation of a temporary private
     * auto-delete queue.
     * <p>
     * The default shouldn't be changed for performance reasons.
     *
     * @param replyTo
     * @return
     */
    public RpcClientParams replyTo(String replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Set the timeout in milliseconds to use on call responses.
     *
     * @param timeout
     * @return
     */
    public RpcClientParams timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Whether to publish RPC requests with the mandatory flag or not.
     * <p>
     * Default is to not publish requests with the mandatory flag
     * set to true.
     * <p>
     * When set to true, unroutable requests will result
     * in {@link UnroutableRpcRequestException} exceptions thrown.
     * Use a custom reply handler to change this behavior.
     *
     * @param useMandatory
     * @return
     * @see #replyHandler(RpcClient.RpcClientReplyHandler)
     */
    public RpcClientParams useMandatory(boolean useMandatory) {
        this.useMandatory = useMandatory;
        return this;
    }

    /**
     * Instructs to use the mandatory flag when publishing RPC requests.
     * <p>
     * Unroutable requests will result in {@link UnroutableRpcRequestException} exceptions
     * thrown. Use a custom reply handler to change this behavior.
     *
     * @return
     * @see #replyHandler(Function)
     */
    public RpcClientParams useMandatory() {
        return useMandatory(true);
    }

    public boolean shouldUseMandatory() {
        return useMandatory;
    }

    public Function<Object, RpcClient.Response> getReplyHandler() {
        return replyHandler;
    }

    /**
     * Set the behavior to use when receiving replies.
     * <p>
     * The default is to wrap the reply into a {@link com.rabbitmq.client.RpcClient.Response}
     * instance. Unroutable requests will result in {@link UnroutableRpcRequestException}
     * exceptions.
     *
     * @param replyHandler
     * @return
     * @see #useMandatory()
     * @see #useMandatory(boolean)
     */
    public RpcClientParams replyHandler(Function<Object, RpcClient.Response> replyHandler) {
        this.replyHandler = replyHandler;
        return this;
    }
}
