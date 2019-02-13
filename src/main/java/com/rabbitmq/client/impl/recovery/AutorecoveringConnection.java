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

package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import com.rabbitmq.client.impl.NetworkConnection;
import com.rabbitmq.utility.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Connection implementation that performs automatic recovery when
 * connection shutdown is not initiated by the application (e.g. due to
 * an I/O exception).
 *
 * Topology (exchanges, queues, bindings, and consumers) can be (and by default is) recovered
 * as well, in this order:
 *
 * <ol>
 *  <li>Exchanges</li>
 *  <li>Queues</li>
 *  <li>Bindings (both queue and exchange-to-exchange)</li>
 *  <li>Consumers</li>
 * </ol>
 *
 * @see com.rabbitmq.client.Connection
 * @see com.rabbitmq.client.Recoverable
 * @see com.rabbitmq.client.ConnectionFactory#setAutomaticRecoveryEnabled(boolean)
 * @see com.rabbitmq.client.ConnectionFactory#setTopologyRecoveryEnabled(boolean)
 * @since 3.3.0
 */
public class AutorecoveringConnection implements RecoverableConnection, NetworkConnection {

    public static final Predicate<ShutdownSignalException> DEFAULT_CONNECTION_RECOVERY_TRIGGERING_CONDITION =
        cause -> !cause.isInitiatedByApplication() || (cause.getCause() instanceof MissedHeartbeatException);

    private static final Logger LOGGER = LoggerFactory.getLogger(AutorecoveringConnection.class);

    private final RecoveryAwareAMQConnectionFactory cf;
    private final Map<Integer, AutorecoveringChannel> channels;
    private final ConnectionParams params;
    private volatile RecoveryAwareAMQConnection delegate;

    private final List<ShutdownListener> shutdownHooks  = Collections.synchronizedList(new ArrayList<>());
    private final List<RecoveryListener> recoveryListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<BlockedListener> blockedListeners = Collections.synchronizedList(new ArrayList<>());

    // Records topology changes
    private final Map<String, RecordedQueue> recordedQueues = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<RecordedBinding> recordedBindings = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, RecordedExchange> recordedExchanges = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, RecordedConsumer> consumers = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<ConsumerRecoveryListener> consumerRecoveryListeners = Collections.synchronizedList(new ArrayList<>());
    private final List<QueueRecoveryListener> queueRecoveryListeners = Collections.synchronizedList(new ArrayList<>());

    private final TopologyRecoveryFilter topologyRecoveryFilter;
	
	// Used to block connection recovery attempts after close() is invoked.
	private volatile boolean manuallyClosed = false;
	
	// This lock guards the manuallyClosed flag and the delegate connection.  Guarding these two ensures that a new connection can never
	// be created after application code has initiated shutdown.  
	private final Object recoveryLock = new Object();

	private final Predicate<ShutdownSignalException> connectionRecoveryTriggeringCondition;

	private final RetryHandler retryHandler;

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, List<Address> addrs) {
        this(params, f, new ListAddressResolver(addrs));
    }

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, AddressResolver addressResolver) {
        this(params, f, addressResolver, new NoOpMetricsCollector());
    }

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, AddressResolver addressResolver, MetricsCollector metricsCollector) {
        this.cf = new RecoveryAwareAMQConnectionFactory(params, f, addressResolver, metricsCollector);
        this.params = params;

        this.connectionRecoveryTriggeringCondition = params.getConnectionRecoveryTriggeringCondition() == null ?
            DEFAULT_CONNECTION_RECOVERY_TRIGGERING_CONDITION : params.getConnectionRecoveryTriggeringCondition();

        setupErrorOnWriteListenerForPotentialRecovery();

        this.channels = new ConcurrentHashMap<>();
        this.topologyRecoveryFilter = params.getTopologyRecoveryFilter() == null ?
            letAllPassFilter() : params.getTopologyRecoveryFilter();

        this.retryHandler = params.getTopologyRecoveryRetryHandler();
    }

    private void setupErrorOnWriteListenerForPotentialRecovery() {
        final ThreadFactory threadFactory = this.params.getThreadFactory();
        final Lock errorOnWriteLock = new ReentrantLock();
        this.params.setErrorOnWriteListener((connection, exception) -> {
            // this is called for any write error
            // we should trigger the error handling and the recovery only once
            if (errorOnWriteLock.tryLock()) {
                try {
                    Thread recoveryThread = threadFactory.newThread(() -> {
                        AMQConnection c = (AMQConnection) connection;
                        c.handleIoError(exception);
                    });
                    recoveryThread.setName("RabbitMQ Error On Write Thread");
                    recoveryThread.start();
                } finally {
                    errorOnWriteLock.unlock();
                }
            }
            throw exception;
        });
    }

    private TopologyRecoveryFilter letAllPassFilter() {
        return new TopologyRecoveryFilter() {};
    }

    /**
     * Private API.
     * @throws IOException
     * @see com.rabbitmq.client.ConnectionFactory#newConnection(java.util.concurrent.ExecutorService)
     */
    public void init() throws IOException, TimeoutException {
        this.delegate = this.cf.newConnection();
        this.addAutomaticRecoveryListener(delegate);
    }

    /**
     * @see com.rabbitmq.client.Connection#createChannel()
     */
    @Override
    public Channel createChannel() throws IOException {
        RecoveryAwareChannelN ch = (RecoveryAwareChannelN) delegate.createChannel();
        // No Sonar: the channel could be null
        if (ch == null) { //NOSONAR
            return null;
        } else {
            return this.wrapChannel(ch);
        }
    }

    /**
     * @see com.rabbitmq.client.Connection#createChannel(int)
     */
    @Override
    public Channel createChannel(int channelNumber) throws IOException {
        return delegate.createChannel(channelNumber);
    }

    /**
     * Creates a recovering channel from a regular channel and registers it.
     * If the regular channel cannot be created (e.g. too many channels are open
     * already), returns null.
     *
     * @param delegateChannel Channel to wrap.
     * @return Recovering channel.
     */
    private Channel wrapChannel(RecoveryAwareChannelN delegateChannel) {
        if (delegateChannel == null) {
            return null;
        } else {
            final AutorecoveringChannel channel = new AutorecoveringChannel(this, delegateChannel);
            this.registerChannel(channel);
            return channel;
        }
    }

    void registerChannel(AutorecoveringChannel channel) {
        this.channels.put(channel.getChannelNumber(), channel);
    }

    void unregisterChannel(AutorecoveringChannel channel) {
        this.channels.remove(channel.getChannelNumber());
    }

    /**
     * @see com.rabbitmq.client.Connection#getServerProperties()
     */
    @Override
    public Map<String, Object> getServerProperties() {
        return delegate.getServerProperties();
    }

    /**
     * @see com.rabbitmq.client.Connection#getClientProperties()
     */
    @Override
    public Map<String, Object> getClientProperties() {
        return delegate.getClientProperties();
    }

    /**
     * @see com.rabbitmq.client.Connection#getClientProvidedName()
     * @see ConnectionFactory#newConnection(Address[], String)
     * @see ConnectionFactory#newConnection(ExecutorService, Address[], String)
     */
    @Override
    public String getClientProvidedName() {
        return delegate.getClientProvidedName();
    }

    /**
     * @see com.rabbitmq.client.Connection#getFrameMax()
     */
    @Override
    public int getFrameMax() {
        return delegate.getFrameMax();
    }

    /**
     * @see com.rabbitmq.client.Connection#getHeartbeat()
     */
    @Override
    public int getHeartbeat() {
        return delegate.getHeartbeat();
    }

    /**
     * @see com.rabbitmq.client.Connection#getChannelMax()
     */
    @Override
    public int getChannelMax() {
        return delegate.getChannelMax();
    }

    /**
     * @see com.rabbitmq.client.Connection#isOpen()
     */
    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    /**
     * @see com.rabbitmq.client.Connection#close()
     */
    @Override
    public void close() throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close();
    }

    /**
     * @see Connection#close(int)
     */
    @Override
    public void close(int timeout) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close(timeout);
    }

    /**
     * @see Connection#close(int, String, int)
     */
    @Override
    public void close(int closeCode, String closeMessage, int timeout) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close(closeCode, closeMessage, timeout);
    }

    /**
     * @see com.rabbitmq.client.Connection#abort()
     */
    @Override
    public void abort() {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort();
    }

    /**
     * @see Connection#abort(int, String, int)
     */
    @Override
    public void abort(int closeCode, String closeMessage, int timeout) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(closeCode, closeMessage, timeout);
    }

    /**
     * @see Connection#abort(int, String)
     */
    @Override
    public void abort(int closeCode, String closeMessage) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(closeCode, closeMessage);
    }

    /**
     * @see Connection#abort(int)
     */
    @Override
    public void abort(int timeout) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(timeout);
    }

    /**
    * Not supposed to be used outside of automated tests.
    */
    public AMQConnection getDelegate() {
        return delegate;
    }

    /**
     * @see com.rabbitmq.client.Connection#getCloseReason()
     */
    @Override
    public ShutdownSignalException getCloseReason() {
        return delegate.getCloseReason();
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    @Override
    public void addBlockedListener(BlockedListener listener) {
        this.blockedListeners.add(listener);
        delegate.addBlockedListener(listener);
    }

    @Override
    public BlockedListener addBlockedListener(BlockedCallback blockedCallback, UnblockedCallback unblockedCallback) {
        BlockedListener blockedListener = new BlockedListener() {

            @Override
            public void handleBlocked(String reason) throws IOException {
                blockedCallback.handle(reason);
            }

            @Override
            public void handleUnblocked() throws IOException {
                unblockedCallback.handle();
            }
        };
        this.addBlockedListener(blockedListener);
        return blockedListener;
    }

    /**
     * @see Connection#removeBlockedListener(com.rabbitmq.client.BlockedListener)
     */
    @Override
    public boolean removeBlockedListener(BlockedListener listener) {
        this.blockedListeners.remove(listener);
        return delegate.removeBlockedListener(listener);
    }

    /**
     * @see com.rabbitmq.client.Connection#clearBlockedListeners()
     */
    @Override
    public void clearBlockedListeners() {
        this.blockedListeners.clear();
        delegate.clearBlockedListeners();
    }

    /**
     * @see com.rabbitmq.client.Connection#close(int, String)
     */
    @Override
    public void close(int closeCode, String closeMessage) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
		delegate.close(closeCode, closeMessage);
    }

    /**
     * @see Connection#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    @Override
    public void addShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.add(listener);
        delegate.addShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#removeShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    @Override
    public void removeShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.remove(listener);
        delegate.removeShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#notifyListeners()
     */
    @Override
    public void notifyListeners() {
        delegate.notifyListeners();
    }

    /**
     * Adds the recovery listener
     * @param listener {@link com.rabbitmq.client.RecoveryListener} to execute after this connection recovers from network failure
     */
    @Override
    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    /**
     * Removes the recovery listener
     * @param listener {@link com.rabbitmq.client.RecoveryListener} to remove
     */
    @Override
    public void removeRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.remove(listener);
    }

    /**
     * @see com.rabbitmq.client.impl.AMQConnection#getExceptionHandler()
     */
    @Override
    public ExceptionHandler getExceptionHandler() {
        return this.delegate.getExceptionHandler();
    }

    /**
     * @see com.rabbitmq.client.Connection#getPort()
     */
    @Override
    public int getPort() {
        return delegate.getPort();
    }

    /**
     * @see com.rabbitmq.client.Connection#getAddress()
     */
    @Override
    public InetAddress getAddress() {
        return delegate.getAddress();
    }

    /**
     * @return client socket address
     */
    @Override
    public InetAddress getLocalAddress() {
        return this.delegate.getLocalAddress();
    }

    /**
     * @return client socket port
     */
    @Override
    public int getLocalPort() {
        return this.delegate.getLocalPort();
    }

    //
    // Recovery
    //

    private void addAutomaticRecoveryListener(final RecoveryAwareAMQConnection newConn) {
        final AutorecoveringConnection c = this;
        // this listener will run after shutdown listeners,
        // see https://github.com/rabbitmq/rabbitmq-java-client/issues/135
        RecoveryCanBeginListener starter = cause -> {
            try {
                if (shouldTriggerConnectionRecovery(cause)) {
                    c.beginAutomaticRecovery();
                }
            } catch (Exception e) {
                newConn.getExceptionHandler().handleConnectionRecoveryException(c, e);
            }
        };
        synchronized (this) {
            newConn.addRecoveryCanBeginListener(starter);
        }
    }

    protected boolean shouldTriggerConnectionRecovery(ShutdownSignalException cause) {
        return connectionRecoveryTriggeringCondition.test(cause);
    }

    /**
     * Not part of the public API. Mean to be used by JVM RabbitMQ clients that build on
     * top of the Java client and need to be notified when server-named queue name changes
     * after recovery.
     *
     * @param listener listener that observes queue name changes after recovery
     */
    public void addQueueRecoveryListener(QueueRecoveryListener listener) {
        this.queueRecoveryListeners.add(listener);
    }

    /**
     * @see com.rabbitmq.client.impl.recovery.AutorecoveringConnection#addQueueRecoveryListener
     * @param listener listener to be removed
     */
    public void removeQueueRecoveryListener(QueueRecoveryListener listener) {
        this.queueRecoveryListeners.remove(listener);
    }

    /**
     * Not part of the public API. Mean to be used by JVM RabbitMQ clients that build on
     * top of the Java client and need to be notified when consumer tag changes
     * after recovery.
     *
     * @param listener listener that observes consumer tag changes after recovery
     */
    public void addConsumerRecoveryListener(ConsumerRecoveryListener listener) {
        this.consumerRecoveryListeners.add(listener);
    }

    /**
     * @see com.rabbitmq.client.impl.recovery.AutorecoveringConnection#addConsumerRecoveryListener(ConsumerRecoveryListener)
     * @param listener listener to be removed
     */
    public void removeConsumerRecoveryListener(ConsumerRecoveryListener listener) {
        this.consumerRecoveryListeners.remove(listener);
    }

    private synchronized void beginAutomaticRecovery() throws InterruptedException {
        this.wait(this.params.getRecoveryDelayHandler().getDelay(0));

        this.notifyRecoveryListenersStarted();

        final RecoveryAwareAMQConnection newConn = this.recoverConnection();
        if (newConn == null) {
            return;
        }
        LOGGER.debug("Connection {} has recovered", newConn);
        this.addAutomaticRecoveryListener(newConn);
	    this.recoverShutdownListeners(newConn);
	    this.recoverBlockedListeners(newConn);
	    this.recoverChannels(newConn);
	    // don't assign new delegate connection until channel recovery is complete
	    this.delegate = newConn;
	    if (this.params.isTopologyRecoveryEnabled()) {
	        recoverTopology(params.getTopologyRecoveryExecutor());
	    }
		this.notifyRecoveryListenersComplete();
    }

    private void recoverShutdownListeners(final RecoveryAwareAMQConnection newConn) {
        for (ShutdownListener sh : Utility.copy(this.shutdownHooks)) {
            newConn.addShutdownListener(sh);
        }
    }

    private void recoverBlockedListeners(final RecoveryAwareAMQConnection newConn) {
        for (BlockedListener bl : Utility.copy(this.blockedListeners)) {
            newConn.addBlockedListener(bl);
        }
    }

	// Returns new connection if the connection was recovered, 
	// null if application initiated shutdown while attempting recovery.  
    private RecoveryAwareAMQConnection recoverConnection() throws InterruptedException {
        int attempts = 0;
        while (!manuallyClosed) {
            try {
                attempts++;
                // No Sonar: no need to close this resource because we're the one that creates it
                // and hands it over to the user
				RecoveryAwareAMQConnection newConn = this.cf.newConnection(); //NOSONAR
				synchronized(recoveryLock) {
					if (!manuallyClosed) {
						// This is the standard case.				
						return newConn;
					}
				}
				// This is the once in a blue moon case.  
				// Application code just called close as the connection
				// was being re-established.  So we attempt to close the newly created connection.
				newConn.abort();
				return null;
            } catch (Exception e) {
                Thread.sleep(this.params.getRecoveryDelayHandler().getDelay(attempts));
                this.getExceptionHandler().handleConnectionRecoveryException(this, e);
            }
        }
		
		return null;
    }

    private void recoverChannels(final RecoveryAwareAMQConnection newConn) {
        for (AutorecoveringChannel ch : this.channels.values()) {
            try {
                ch.automaticallyRecover(this, newConn);
                LOGGER.debug("Channel {} has recovered", ch);
            } catch (Throwable t) {
                newConn.getExceptionHandler().handleChannelRecoveryException(ch, t);
            }
        }
    }

    void recoverChannel(AutorecoveringChannel channel) throws IOException {
        channel.automaticallyRecover(this, this.delegate);
    }

    private void notifyRecoveryListenersComplete() {
        for (RecoveryListener f : Utility.copy(this.recoveryListeners)) {
            f.handleRecovery(this);
        }
    }

    private void notifyRecoveryListenersStarted() {
        for (RecoveryListener f : Utility.copy(this.recoveryListeners)) {
            f.handleRecoveryStarted(this);
        }
    }
    
    private void recoverTopology(final ExecutorService executor) {
        // The recovery sequence is the following:
        // 1. Recover exchanges
        // 2. Recover queues
        // 3. Recover bindings
        // 4. Recover consumers
        if (executor == null) {
            // recover entities in serial on the main connection thread
            for (final RecordedExchange exchange : Utility.copy(recordedExchanges).values()) {
                recoverExchange(exchange, true);
            }
            for (final Map.Entry<String, RecordedQueue> entry : Utility.copy(recordedQueues).entrySet()) {
                recoverQueue(entry.getKey(), entry.getValue(), true);
            }
            for (final RecordedBinding b : Utility.copy(recordedBindings)) {
                recoverBinding(b, true);
            }
            for (final Map.Entry<String, RecordedConsumer> entry : Utility.copy(consumers).entrySet()) {
                recoverConsumer(entry.getKey(), entry.getValue(), true);
            }
        } else {
            // Support recovering entities in parallel for connections that have a lot of queues, bindings, & consumers
            // A channel is single threaded, so group things by channel and recover 1 entity at a time per channel
            // We also need to recover 1 type of entity at a time in case channel1 has a binding to a queue that is currently owned and being recovered by channel2 for example
            // Note: invokeAll will block until all callables are completed and all returned futures will be complete 
            try {
                recoverEntitiesAsynchronously(executor, Utility.copy(recordedExchanges).values());
                recoverEntitiesAsynchronously(executor, Utility.copy(recordedQueues).values());
                recoverEntitiesAsynchronously(executor, Utility.copy(recordedBindings));
                recoverEntitiesAsynchronously(executor, Utility.copy(consumers).values());
            } catch (final Exception cause) {
                final String message = "Caught an exception while recovering topology: " + cause.getMessage();
                final TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                getExceptionHandler().handleTopologyRecoveryException(delegate, null, e);
            }
        }
    }

    private void recoverExchange(RecordedExchange x, boolean retry) {
        // recorded exchanges are guaranteed to be non-predefined (we filter out predefined ones in exchangeDeclare). MK.
        try {
            if (topologyRecoveryFilter.filterExchange(x)) {
                if (retry) {
                    final RecordedExchange entity = x;
                    x = (RecordedExchange) wrapRetryIfNecessary(x, () -> {
                        entity.recover();
                        return null;
                    }).getRecordedEntity();
                } else {
                    x.recover();
                }
                LOGGER.debug("{} has recovered", x);
            }
        } catch (Exception cause) {
            final String message = "Caught an exception while recovering exchange " + x.getName() +
                    ": " + cause.getMessage();
            TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
            this.getExceptionHandler().handleTopologyRecoveryException(delegate, x.getDelegateChannel(), e);
        }
    }


    public void recoverQueue(final String oldName, RecordedQueue q, boolean retry) {
        try {
            if (topologyRecoveryFilter.filterQueue(q)) {
                LOGGER.debug("Recovering {}", q);
                if (retry) {
                    final RecordedQueue entity = q;
                    q = (RecordedQueue) wrapRetryIfNecessary(q, () -> {
                        entity.recover();
                        return null;
                    }).getRecordedEntity();
                } else {
                    q.recover();
                }
                String newName = q.getName();
                if (!oldName.equals(newName)) {
                    // make sure server-named queues are re-added with
                    // their new names. MK.
                    synchronized (this.recordedQueues) {
                        this.propagateQueueNameChangeToBindings(oldName, newName);
                        this.propagateQueueNameChangeToConsumers(oldName, newName);
                        // bug26552:
                        // remove old name after we've updated the bindings and consumers,
                        // plus only for server-named queues, both to make sure we don't lose
                        // anything to recover. MK.
                        if(q.isServerNamed()) {
                            deleteRecordedQueue(oldName);
                        }
                        this.recordedQueues.put(newName, q);
                    }
                }
                for (QueueRecoveryListener qrl : Utility.copy(this.queueRecoveryListeners)) {
                    qrl.queueRecovered(oldName, newName);
                }
                LOGGER.debug("{} has recovered", q);
            }
        } catch (Exception cause) {
            final String message = "Caught an exception while recovering queue " + oldName +
                                           ": " + cause.getMessage();
            TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
            this.getExceptionHandler().handleTopologyRecoveryException(delegate, q.getDelegateChannel(), e);
        }
    }

    private void recoverBinding(RecordedBinding b, boolean retry) {
        try {
            if (this.topologyRecoveryFilter.filterBinding(b)) {
                if (retry) {
                    final RecordedBinding entity = b;
                    b = (RecordedBinding) wrapRetryIfNecessary(b, () -> {
                        entity.recover();
                        return null;
                    }).getRecordedEntity();
                } else {
                    b.recover();
                }
                LOGGER.debug("{} has recovered", b);
            }
        } catch (Exception cause) {
            String message = "Caught an exception while recovering binding between " + b.getSource() +
                                     " and " + b.getDestination() + ": " + cause.getMessage();
            TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
            this.getExceptionHandler().handleTopologyRecoveryException(delegate, b.getDelegateChannel(), e);
        }
    }

    public void recoverConsumer(final String tag, RecordedConsumer consumer, boolean retry) {
        try {
            if (this.topologyRecoveryFilter.filterConsumer(consumer)) {
                LOGGER.debug("Recovering {}", consumer);
                String newTag = null;
                if (retry) {
                    final RecordedConsumer entity = consumer;
                    RetryResult retryResult = wrapRetryIfNecessary(consumer, () -> entity.recover());
                    consumer = (RecordedConsumer) retryResult.getRecordedEntity();
                    newTag = (String) retryResult.getResult();
                } else {
                    newTag = consumer.recover();
                }

                // make sure server-generated tags are re-added. MK.
                if(tag != null && !tag.equals(newTag)) {
                    synchronized (this.consumers) {
                        this.consumers.remove(tag);
                        this.consumers.put(newTag, consumer);
                    }
                    consumer.getChannel().updateConsumerTag(tag, newTag);
                }

                for (ConsumerRecoveryListener crl : Utility.copy(this.consumerRecoveryListeners)) {
                    crl.consumerRecovered(tag, newTag);
                }
                LOGGER.debug("{} has recovered", consumer);
            }
        } catch (Exception cause) {
            final String message = "Caught an exception while recovering consumer " + tag +
                    ": " + cause.getMessage();
            TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
            this.getExceptionHandler().handleTopologyRecoveryException(delegate, consumer.getDelegateChannel(), e);
        }
    }

    private <T> RetryResult wrapRetryIfNecessary(RecordedEntity entity, Callable<T> recoveryAction) throws Exception {
        if (this.retryHandler == null) {
            T result = recoveryAction.call();
            return new RetryResult(entity, result);
        } else {
            try {
                T result = recoveryAction.call();
                return new RetryResult(entity, result);
            } catch (Exception e) {
                RetryContext retryContext = new RetryContext(entity, e, this);
                RetryResult retryResult;
                if (entity instanceof RecordedQueue) {
                    retryResult = this.retryHandler.retryQueueRecovery(retryContext);
                } else if (entity instanceof RecordedExchange) {
                    retryResult = this.retryHandler.retryExchangeRecovery(retryContext);
                } else if (entity instanceof RecordedBinding) {
                    retryResult = this.retryHandler.retryBindingRecovery(retryContext);
                } else if (entity instanceof RecordedConsumer) {
                    retryResult = this.retryHandler.retryConsumerRecovery(retryContext);
                } else {
                    throw new IllegalArgumentException("Unknown type of recorded entity: " + entity);
                }
                return retryResult;
            }
        }
    }

    private void propagateQueueNameChangeToBindings(String oldName, String newName) {
        for (RecordedBinding b : Utility.copy(this.recordedBindings)) {
            if (b.getDestination().equals(oldName)) {
                b.setDestination(newName);
            }
        }
    }

    private void propagateQueueNameChangeToConsumers(String oldName, String newName) {
        for (RecordedConsumer c : Utility.copy(this.consumers).values()) {
            if (c.getQueue().equals(oldName)) {
                c.setQueue(newName);
            }
        }
    }

    private void recoverEntitiesAsynchronously(ExecutorService executor, Collection<? extends RecordedEntity> recordedEntities) throws InterruptedException {
        List<Future<Object>> tasks = executor.invokeAll(groupEntitiesByChannel(recordedEntities));
        for (Future<Object> task : tasks) {
            if (!task.isDone()) {
                LOGGER.warn("Recovery task should be done {}", task);
            } else {
                try {
                    task.get(1, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    LOGGER.warn("Recovery task is done but returned an exception", e);
                }
            }
        }
    }

    private <E extends RecordedEntity> List<Callable<Object>> groupEntitiesByChannel(final Collection<E> entities) {
        // map entities by channel
        final Map<AutorecoveringChannel, List<E>> map = new LinkedHashMap<AutorecoveringChannel, List<E>>();
        for (final E entity : entities) {
            final AutorecoveringChannel channel = entity.getChannel();
            List<E> list = map.get(channel);
            if (list == null) {
                map.put(channel, list = new ArrayList<E>());
            }
            list.add(entity);
        }
        // now create a runnable per channel
        final List<Callable<Object>> callables = new ArrayList<>();
        for (final List<E> entityList : map.values()) {
            callables.add(Executors.callable(() -> {
                for (final E entity : entityList) {
                    if (entity instanceof RecordedExchange) {
                        recoverExchange((RecordedExchange)entity, true);
                    } else if (entity instanceof RecordedQueue) {
                        final RecordedQueue q = (RecordedQueue) entity;
                        recoverQueue(q.getName(), q, true);
                    } else if (entity instanceof RecordedBinding) {
                        recoverBinding((RecordedBinding) entity, true);
                    } else if (entity instanceof RecordedConsumer) {
                        final RecordedConsumer c = (RecordedConsumer) entity;
                        recoverConsumer(c.getConsumerTag(), c, true);
                    }
                }
            }));
        }
        return callables;
    }

    void recordQueueBinding(AutorecoveringChannel ch,
                                                String queue,
                                                String exchange,
                                                String routingKey,
                                                Map<String, Object> arguments) {
        RecordedBinding binding = new RecordedQueueBinding(ch).
                                         source(exchange).
                                         destination(queue).
                                         routingKey(routingKey).
                                         arguments(arguments);
        this.recordedBindings.remove(binding);
        this.recordedBindings.add(binding);
    }

    boolean deleteRecordedQueueBinding(AutorecoveringChannel ch,
                                                           String queue,
                                                           String exchange,
                                                           String routingKey,
                                                           Map<String, Object> arguments) {
        RecordedBinding b = new RecordedQueueBinding(ch).
                                   source(exchange).
                                   destination(queue).
                                   routingKey(routingKey).
                                   arguments(arguments);
        return this.recordedBindings.remove(b);
    }

    void recordExchangeBinding(AutorecoveringChannel ch,
                                                   String destination,
                                                   String source,
                                                   String routingKey,
                                                   Map<String, Object> arguments) {
        RecordedBinding binding = new RecordedExchangeBinding(ch).
                                          source(source).
                                          destination(destination).
                                          routingKey(routingKey).
                                          arguments(arguments);
        this.recordedBindings.remove(binding);
        this.recordedBindings.add(binding);
    }

    boolean deleteRecordedExchangeBinding(AutorecoveringChannel ch,
                                                              String destination,
                                                              String source,
                                                              String routingKey,
                                                              Map<String, Object> arguments) {
        RecordedBinding b = new RecordedExchangeBinding(ch).
                                    source(source).
                                    destination(destination).
                                    routingKey(routingKey).
                                    arguments(arguments);
        return this.recordedBindings.remove(b);
    }

    void recordQueue(AMQP.Queue.DeclareOk ok, RecordedQueue q) {
        this.recordedQueues.put(ok.getQueue(), q);
    }

    void recordQueue(String queue, RecordedQueue meta) {
        this.recordedQueues.put(queue, meta);
    }

    void deleteRecordedQueue(String queue) {
        this.recordedQueues.remove(queue);
        Set<RecordedBinding> xs = this.removeBindingsWithDestination(queue);
        for (RecordedBinding b : xs) {
            this.maybeDeleteRecordedAutoDeleteExchange(b.getSource());
        }
    }
    
    /**
     * Exclude the queue from the list of queues to recover after connection failure.
     * Intended to be used in usecases where you want to remove the queue from this connection's recovery list but don't want to delete the queue from the server.
     * 
     * @param queue queue name to exclude from recorded recovery queues
     * @param ifUnused if true, the RecordedQueue will only be excluded if no local consumers are using it.
     */
    public void excludeQueueFromRecovery(final String queue, final boolean ifUnused) {
        if (ifUnused) {
            // Note: This is basically the same as maybeDeleteRecordedAutoDeleteQueue except it works for non auto-delete queues as well.
            synchronized (this.consumers) {
                synchronized (this.recordedQueues) {
                    if (!hasMoreConsumersOnQueue(this.consumers.values(), queue)) {
                        deleteRecordedQueue(queue);
                    }
                }
            }
        } else {
            deleteRecordedQueue(queue);
        }
    }

    void recordExchange(String exchange, RecordedExchange x) {
        this.recordedExchanges.put(exchange, x);
    }

    void deleteRecordedExchange(String exchange) {
        this.recordedExchanges.remove(exchange);
        Set<RecordedBinding> xs = this.removeBindingsWithDestination(exchange);
        for (RecordedBinding b : xs) {
            this.maybeDeleteRecordedAutoDeleteExchange(b.getSource());
        }
    }

    void recordConsumer(String result, RecordedConsumer consumer) {
        this.consumers.put(result, consumer);
    }

    RecordedConsumer deleteRecordedConsumer(String consumerTag) {
        return this.consumers.remove(consumerTag);
    }

    void maybeDeleteRecordedAutoDeleteQueue(String queue) {
        synchronized (this.consumers) {
            synchronized (this.recordedQueues) {
                if(!hasMoreConsumersOnQueue(this.consumers.values(), queue)) {
                    RecordedQueue q = this.recordedQueues.get(queue);
                    // last consumer on this connection is gone, remove recorded queue
                    // if it is auto-deleted. See bug 26364.
                    if(q != null && q.isAutoDelete()) {
                        deleteRecordedQueue(queue);
                    }
                }
            }
        }
    }

    void maybeDeleteRecordedAutoDeleteExchange(String exchange) {
        synchronized (this.consumers) {
            synchronized (this.recordedExchanges) {
                if(!hasMoreDestinationsBoundToExchange(Utility.copy(this.recordedBindings), exchange)) {
                    RecordedExchange x = this.recordedExchanges.get(exchange);
                    // last binding where this exchange is the source is gone, remove recorded exchange
                    // if it is auto-deleted. See bug 26364.
                    if(x != null && x.isAutoDelete()) {
                        deleteRecordedExchange(exchange);
                    }
                }
            }
        }
    }

    boolean hasMoreDestinationsBoundToExchange(List<RecordedBinding> bindings, String exchange) {
        boolean result = false;
        for (RecordedBinding b : bindings) {
            if(exchange.equals(b.getSource())) {
                result = true;
                break;
            }
        }
        return result;
    }

    boolean hasMoreConsumersOnQueue(Collection<RecordedConsumer> consumers, String queue) {
        boolean result = false;
        for (RecordedConsumer c : consumers) {
            if(queue.equals(c.getQueue())) {
                result = true;
                break;
            }
        }
        return result;
    }

    Set<RecordedBinding> removeBindingsWithDestination(String s) {
        final Set<RecordedBinding> result = new HashSet<RecordedBinding>();
        synchronized (this.recordedBindings) {
            for (Iterator<RecordedBinding> it = this.recordedBindings.iterator(); it.hasNext(); ) {
                RecordedBinding b = it.next();
                if(b.getDestination().equals(s)) {
                    it.remove();
                    result.add(b);
                }
            }
        }
        return result;
    }

    public Map<String, RecordedQueue> getRecordedQueues() {
        return recordedQueues;
    }

    public Map<String, RecordedExchange> getRecordedExchanges() {
        return recordedExchanges;
    }

    public List<RecordedBinding> getRecordedBindings() {
        return recordedBindings;
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }

    /** Public API - {@inheritDoc} */
    @Override
    public String getId() {
        return this.delegate.getId();
    }

    /** Public API - {@inheritDoc} */
    @Override
    public void setId(String id) {
        this.delegate.setId(id);
    }
}
