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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.Utility;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Dispatches notifications to a {@link Consumer} on an
 * internally-managed executor service and work pool.
 * <p/>
 * Each {@link Channel} has a single {@link ConsumerDispatcher},
 * but the executor service and work pool may be shared with other channels, typically those on the same
 * {@link Connection}.
 */
public final class ConsumerDispatcher {

    private final ConsumerWorkService workService;

    private final AMQConnection connection;

    private final Channel channel;

    private volatile ShutdownSignalException shutdownSignal;

    public ConsumerDispatcher(AMQConnection connection,
                              Channel channel,
                              ConsumerWorkService workService) {
        this.connection = connection;
        this.channel = channel;
        workService.registerKey(channel);
        this.workService = workService;
    }

    public void handleConsumeOk(final Consumer delegate,
                                final String consumerTag) {
        execute(new Runnable() {

            public void run() {
                try {
                    delegate.handleConsumeOk(consumerTag);
                } catch (Throwable ex) {
                    connection.getExceptionHandler().handleConsumerException(
                            channel,
                            ex,
                            delegate,
                            consumerTag,
                            "handleConsumeOk");
                }
            }
        });
    }

    public void handleCancelOk(final Consumer delegate,
                               final String consumerTag) {
        execute(new Runnable() {

            public void run() {
                try {
                    delegate.handleCancelOk(consumerTag);
                } catch (Throwable ex) {
                    connection.getExceptionHandler().handleConsumerException(
                            channel,
                            ex,
                            delegate,
                            consumerTag,
                            "handleCancelOk");
                }
            }
        });
    }

    public void handleRecoverOk(final Consumer delegate) {
        execute(new Runnable() {
            public void run() {
                delegate.handleRecoverOk();
            }
        });
    }

    public void handleDelivery(final Consumer delegate,
                               final String consumerTag,
                               final Envelope envelope,
                               final AMQP.BasicProperties properties,
                               final byte[] body) throws IOException {
        execute(new Runnable() {
            public void run() {
                try {
                    delegate.handleDelivery(consumerTag,
                            envelope,
                            properties,
                            body);
                } catch (Throwable ex) {
                    connection.getExceptionHandler().handleConsumerException(
                            channel,
                            ex,
                            delegate,
                            consumerTag,
                            "handleDelivery");
                }
            }
        });
    }

    public void handleShutdownSignal(final Map<String, Consumer> consumers,
                                     final ShutdownSignalException signal) {
        execute(new Runnable() {
            public void run() {
                notifyConsumersOfShutdown(consumers, signal);
                ConsumerDispatcher.this.shutdown(signal);
            }
        });
    }

    private void notifyConsumersOfShutdown(Map<String, Consumer> consumers,
                                           ShutdownSignalException signal) {
        Set<Map.Entry<String, Consumer>> entries = consumers.entrySet();
        for (Map.Entry<String, Consumer> consumerEntry : entries) {
            Consumer consumer = consumerEntry.getValue();
            String consumerTag = consumerEntry.getKey();
            notifyConsumerOfShutdown(consumer, consumerTag, signal);
        }
    }

    private void notifyConsumerOfShutdown(Consumer consumer,
                                          String consumerTag,
                                          ShutdownSignalException signal) {
        try {
            consumer.handleShutdownSignal(consumerTag, signal);
        } catch (Throwable ex) {
            connection.getExceptionHandler().handleConsumerException(
                    channel,
                    ex,
                    consumer,
                    consumerTag,
                    "handleShutdownSignal");
        }
    }

    private void execute(Runnable r) {
        checkShutdown();
        this.workService.addWork(this.channel, r);
    }

    private void shutdown(ShutdownSignalException signal) {
        this.shutdownSignal = signal;
    }

    private void checkShutdown() {
        if (this.shutdownSignal != null) {
            throw Utility.fixStackTrace(this.shutdownSignal);
        }
    }
}
