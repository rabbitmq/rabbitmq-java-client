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
import java.util.concurrent.Future;

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

    private volatile boolean shuttingDown = false;
    private volatile boolean shutdownHandled = false;
    private volatile Future<Boolean> shutdownDone;

    private volatile ShutdownSignalException shutdownSignal;

    public ConsumerDispatcher(AMQConnection connection,
                              Channel channel,
                              ConsumerWorkService workService) {
        this.connection = connection;
        this.channel = channel;
        workService.registerKey(channel);
        this.workService = workService;
    }

    /** Prepare for shutdown of all consumers on this channel */
    public void quiesce() {
        // Prevent any more items being put on the queue (except the shutdown item)
        this.shuttingDown = true;
    }

    public void handleConsumeOk(final Consumer delegate,
                                final String consumerTag) {
        if (this.shuttingDown) return;
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
        if (this.shuttingDown) return;
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
        if (this.shuttingDown) return;
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
        if (this.shuttingDown) return;
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

    public Future<Boolean> handleShutdownSignal(final Map<String, Consumer> consumers,
                                     final ShutdownSignalException signal) {
        // ONLY CASE WHERE WE IGNORE shuttingDown
        if (!this.shutdownHandled) {
            final SimpleFutureValue<Boolean> done = new SimpleFutureValue<Boolean>();
            this.shutdownDone = done;
            this.shutdownHandled = true;
            // Execute shutdown processing even if there are no consumers.
            execute(new Runnable() {
                public void run() {
                    notifyConsumersOfShutdown(consumers, signal);
                    ConsumerDispatcher.this.shutdown(signal);
                    done.set(true);
                }
            });
        }
        return this.shutdownDone;
    }

    private void notifyConsumersOfShutdown(Map<String, Consumer> consumers,
                                           ShutdownSignalException signal) {
        for (String consumerTag : consumers.keySet()) {
            notifyConsumerOfShutdown(consumerTag, consumers.get(consumerTag), signal);
        }
    }

    private void notifyConsumerOfShutdown(String consumerTag,
                                          Consumer consumer,
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
