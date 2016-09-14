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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.Utility;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Dispatches notifications to a {@link Consumer} on an internally-managed executor service and work
 * pool.
 * <p/>
 * Each {@link Channel} has a single <code>ConsumerDispatcher</code>, but the executor service and work
 * pool may be shared with other channels, typically those on the same {@link AMQConnection}.
 */
final class ConsumerDispatcher {

    private final ConsumerWorkService workService;

    private final AMQConnection connection;

    private final Channel channel;

    private volatile boolean shuttingDown = false;
    private volatile boolean shutdownConsumersDriven = false;
    private volatile CountDownLatch shutdownConsumersComplete;

    private volatile ShutdownSignalException shutdownSignal = null;

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

    public void setUnlimited(boolean unlimited) {
        this.workService.setUnlimited(channel, unlimited);
    }

    public void handleConsumeOk(final Consumer delegate,
                                final String consumerTag) {
        executeUnlessShuttingDown(
        new Runnable() {
            @Override
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
        executeUnlessShuttingDown(
        new Runnable() {
            @Override
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

    public void handleCancel(final Consumer delegate, final String consumerTag) {
        executeUnlessShuttingDown(
        new Runnable() {
      @Override
    public void run() {
                try {
                    delegate.handleCancel(consumerTag);
                } catch (Throwable ex) {
                    connection.getExceptionHandler().handleConsumerException(
                            channel,
                            ex,
                            delegate,
                            consumerTag,
                            "handleCancel");
                }
      }
    });
  }


    public void handleRecoverOk(final Consumer delegate, final String consumerTag) {
        executeUnlessShuttingDown(
        new Runnable() {
            @Override
            public void run() {
                delegate.handleRecoverOk(consumerTag);
            }
        });
    }

    public void handleDelivery(final Consumer delegate,
                               final String consumerTag,
                               final Envelope envelope,
                               final AMQP.BasicProperties properties,
                               final byte[] body) throws IOException {
        executeUnlessShuttingDown(
        new Runnable() {
            @Override
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

    public CountDownLatch handleShutdownSignal(final Map<String, Consumer> consumers,
                                     final ShutdownSignalException signal) {
        // ONLY CASE WHERE WE IGNORE shuttingDown
        if (!this.shutdownConsumersDriven) {
            final CountDownLatch latch = new CountDownLatch(1);
            this.shutdownConsumersComplete = latch;
            this.shutdownConsumersDriven = true;
            // Execute shutdown processing even if there are no consumers.
            execute(new Runnable() {
                @Override
                public void run() {
                    ConsumerDispatcher.this.notifyConsumersOfShutdown(consumers, signal);
                    ConsumerDispatcher.this.shutdown(signal);
                    ConsumerDispatcher.this.workService.stopWork(ConsumerDispatcher.this.channel);
                    latch.countDown();
                }
            });
        }
        return this.shutdownConsumersComplete;
    }

    private void notifyConsumersOfShutdown(Map<String, Consumer> consumers,
                                           ShutdownSignalException signal) {
        for (Map.Entry<String, Consumer> consumerEntry : consumers.entrySet()) {
            notifyConsumerOfShutdown(consumerEntry.getKey(), consumerEntry.getValue(), signal);
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

    private void executeUnlessShuttingDown(Runnable r) {
        if (!this.shuttingDown) execute(r);
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
