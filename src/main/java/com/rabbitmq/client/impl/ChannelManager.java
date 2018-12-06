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

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MetricsCollector;
import com.rabbitmq.client.NoOpMetricsCollector;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.IntAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Manages a set of channels, indexed by channel number (<code><b>1.._channelMax</b></code>).
 */
public class ChannelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelManager.class);

    /** Monitor for <code>_channelMap</code> and <code>channelNumberAllocator</code> */
    private final Object monitor = new Object();
        /** Mapping from <code><b>1.._channelMax</b></code> to {@link ChannelN} instance */
        private final Map<Integer, ChannelN> _channelMap = new HashMap<Integer, ChannelN>();
        private final IntAllocator channelNumberAllocator;

    private final ConsumerWorkService workService;

    private final Set<CountDownLatch> shutdownSet = new HashSet<CountDownLatch>();

    /** Maximum channel number available on this connection. */
    private final int _channelMax;
    private ExecutorService shutdownExecutor;
    private final ThreadFactory threadFactory;

    private int channelShutdownTimeout = (int) ((ConnectionFactory.DEFAULT_HEARTBEAT * AMQConnection.CHANNEL_SHUTDOWN_TIMEOUT_MULTIPLIER) * 1000);

    protected final MetricsCollector metricsCollector;

    public int getChannelMax(){
      return _channelMax;
    }

    public ChannelManager(ConsumerWorkService workService, int channelMax) {
        this(workService, channelMax, Executors.defaultThreadFactory());
    }

    public ChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory) {
        this(workService, channelMax, threadFactory, new NoOpMetricsCollector());
    }


    public ChannelManager(ConsumerWorkService workService, int channelMax, ThreadFactory threadFactory, MetricsCollector metricsCollector) {
        if (channelMax == 0) {
            // The framing encoding only allows for unsigned 16-bit integers
            // for the channel number
            channelMax = (1 << 16) - 1;
        }
        _channelMax = channelMax;
        channelNumberAllocator = new IntAllocator(1, channelMax);

        this.workService = workService;
        this.threadFactory = threadFactory;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Looks up a channel on this connection.
     * @param channelNumber the number of the required channel
     * @return the channel on this connection with number <code><b>channelNumber</b></code>
     * @throws UnknownChannelException if there is no channel with number <code><b>channelNumber</b></code> on this connection
     */
    public ChannelN getChannel(int channelNumber) {
        synchronized (this.monitor) {
            ChannelN ch = _channelMap.get(channelNumber);
            if(ch == null) throw new UnknownChannelException(channelNumber);
            return ch;
        }
    }

    /**
     * Handle shutdown. All the managed {@link com.rabbitmq.client.Channel Channel}s are shutdown.
     * @param signal reason for shutdown
     */
    public void handleSignal(final ShutdownSignalException signal) {
        Set<ChannelN> channels;
        synchronized(this.monitor) {
            channels = new HashSet<ChannelN>(_channelMap.values());
        }

        for (final ChannelN channel : channels) {
            releaseChannelNumber(channel);
            // async shutdown if possible
            // see https://github.com/rabbitmq/rabbitmq-java-client/issues/194
            Runnable channelShutdownRunnable = new Runnable() {
                @Override
                public void run() {
                    channel.processShutdownSignal(signal, true, true);
                }
            };
            if(this.shutdownExecutor == null) {
                channelShutdownRunnable.run();
            } else {
                Future<?> channelShutdownTask = this.shutdownExecutor.submit(channelShutdownRunnable);
                try {
                    channelShutdownTask.get(channelShutdownTimeout, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    LOGGER.warn("Couldn't properly close channel {} on shutdown after waiting for {} ms", channel.getChannelNumber(), channelShutdownTimeout);
                    channelShutdownTask.cancel(true);
                }
            }
            shutdownSet.add(channel.getShutdownLatch());
            channel.notifyListeners();
        }
        scheduleShutdownProcessing();
    }

    private void scheduleShutdownProcessing() {
        final Set<CountDownLatch> sdSet = new HashSet<CountDownLatch>(shutdownSet);
        final ConsumerWorkService ssWorkService = workService;
        Runnable target = new Runnable() {
            @Override
            public void run() {
                for (CountDownLatch latch : sdSet) {
                    try {
                        int shutdownTimeout = ssWorkService.getShutdownTimeout();
                        if (shutdownTimeout == 0) {
                            latch.await();
                        } else {
                            boolean completed = latch.await(shutdownTimeout, TimeUnit.MILLISECONDS);
                            if (!completed) {
                                LOGGER.warn("Consumer dispatcher for channel didn't shutdown after waiting for {} ms", shutdownTimeout);
                            }
                        }
                    } catch (Throwable e) {
                         /*ignored*/
                    }
                }
                ssWorkService.shutdown();
            }
        };
        if(this.shutdownExecutor != null) {
            shutdownExecutor.execute(target);
        } else {
            Thread shutdownThread = Environment.newThread(threadFactory,
                                                          target,
                                                          "ConsumerWorkService shutdown monitor",
                                                          true);
            shutdownThread.start();
        }
    }

    public ChannelN createChannel(AMQConnection connection) throws IOException {
        ChannelN ch;
        synchronized (this.monitor) {
            int channelNumber = channelNumberAllocator.allocate();
            if (channelNumber == -1) {
                return null;
            } else {
                ch = addNewChannel(connection, channelNumber);
            }
        }
        ch.open(); // now that it's been safely added
        return ch;
    }

    public ChannelN createChannel(AMQConnection connection, int channelNumber) throws IOException {
        ChannelN ch;
        synchronized (this.monitor) {
            if (channelNumberAllocator.reserve(channelNumber)) {
                ch = addNewChannel(connection, channelNumber);
            } else {
                return null;
            }
        }
        ch.open(); // now that it's been safely added
        return ch;
    }

    private ChannelN addNewChannel(AMQConnection connection, int channelNumber) {
        if (_channelMap.containsKey(channelNumber)) {
            // That number's already allocated! Can't do it
            // This should never happen unless something has gone
            // badly wrong with our implementation.
            throw new IllegalStateException("We have attempted to "
                    + "create a channel with a number that is already in "
                    + "use. This should never happen. "
                    + "Please report this as a bug.");
        }
        ChannelN ch = instantiateChannel(connection, channelNumber, this.workService);
        _channelMap.put(ch.getChannelNumber(), ch);
        return ch;
    }

    protected ChannelN instantiateChannel(AMQConnection connection, int channelNumber, ConsumerWorkService workService) {
        return new ChannelN(connection, channelNumber, workService, this.metricsCollector);
    }

    /**
     * Remove the channel from the channel map and free the number for re-use.
     * This method must be safe to call multiple times on the same channel. If
     * it is not then things go badly wrong.
     */
    public void releaseChannelNumber(ChannelN channel) {
        // Warning, here be dragons. Not great big ones, but little baby ones
        // which will nibble on your toes and occasionally trip you up when
        // you least expect it. (Pixies? HP2)
        // Basically, there's a race that can end us up here. It almost never
        // happens, but it's easier to repair it when it does than prevent it
        // from happening in the first place.
        // If we end up doing a Channel.close in one thread and a Channel.open
        // with the same channel number in another, the two can overlap in such
        // a way as to cause disconnectChannel on the old channel to try to
        // remove the new one. Ideally we would fix this race at the source,
        // but it's much easier to just catch it here.
        synchronized (this.monitor) {
            int channelNumber = channel.getChannelNumber();
            ChannelN existing = _channelMap.remove(channelNumber);
            // Nothing to do here. Move along.
            if (existing == null)
                return;
            // Oops, we've gone and stomped on someone else's channel. Put it
            // back and pretend we didn't touch it.
            else if (existing != channel) {
                _channelMap.put(channelNumber, existing);
                return;
            }
            channelNumberAllocator.free(channelNumber);
        }
    }

    public ExecutorService getShutdownExecutor() {
        return shutdownExecutor;
    }

    public void setShutdownExecutor(ExecutorService shutdownExecutor) {
        this.shutdownExecutor = shutdownExecutor;
    }

    /**
     * Set the shutdown timeout for channels.
     * This is the amount of time the manager waits for a channel to
     * shutdown before giving up.
     * Works only when the {@code shutdownExecutor} property is set.
     * Default to {@link com.rabbitmq.client.ConnectionFactory#DEFAULT_HEARTBEAT} + 5 % seconds
     * @param channelShutdownTimeout shutdown timeout in milliseconds
     */
    public void setChannelShutdownTimeout(int channelShutdownTimeout) {
        this.channelShutdownTimeout = channelShutdownTimeout;
    }
}
