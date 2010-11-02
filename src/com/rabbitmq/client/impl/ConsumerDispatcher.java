package com.rabbitmq.client.impl;

import com.rabbitmq.client.*;
import com.rabbitmq.utility.Utility;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Simple help class that dispatches notifications to a {@link Consumer} on an
 * internally-managed thread.
 * <p/>
 * Each <code>Channel</code> has a single <code>ConsumerDispatcher</code>,
 * which in turn manages a single thread.
 * <p/>
 * All <code>Consumers</code> for a <code>Channel</code> share the same thread.
 */
final class ConsumerDispatcher {

    private final ExecutorService dispatchExecutor;

    private final AMQConnection connection;

    private final Channel channel;

    private volatile ShutdownSignalException shutdownSignal;

    public ConsumerDispatcher(AMQConnection connection, Channel channel) {
        this.connection = connection;
        this.channel = channel;
        this.dispatchExecutor = Executors.newSingleThreadExecutor(
                new ChannelThreadFactory(channel)
        );
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
                shutdown(signal);
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
        this.dispatchExecutor.submit(r);
    }

    private void shutdown(ShutdownSignalException signal) {
        this.shutdownSignal = signal;
        this.dispatchExecutor.shutdownNow();
    }

    private void checkShutdown() {
        if (this.shutdownSignal != null) {
            throw Utility.fixStackTrace(this.shutdownSignal);
        }
    }

    /**
     * Simple {@link ThreadFactory} implementation that creates threads
     * with names of the form:
     * <code>rabbit-dispatcher-<i>channel.toString()</i></code>.
     */
    private static final class ChannelThreadFactory implements ThreadFactory {

        private static final String PREFIX = "rabbit-dispatcher-";

        private final Channel channel;

        public ChannelThreadFactory(Channel channel) {
            this.channel = channel;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(PREFIX + this.channel.toString());
            thread.setDaemon(true);
            return thread;
        }
    }
}
