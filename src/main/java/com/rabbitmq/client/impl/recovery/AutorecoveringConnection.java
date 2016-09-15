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
import com.rabbitmq.client.impl.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

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
public class AutorecoveringConnection implements Connection, Recoverable, NetworkConnection {
    private final RecoveryAwareAMQConnectionFactory cf;
    private final Map<Integer, AutorecoveringChannel> channels;
    private final ConnectionParams params;
    private RecoveryAwareAMQConnection delegate;

    private final List<ShutdownListener> shutdownHooks  = new ArrayList<ShutdownListener>();
    private final List<RecoveryListener> recoveryListeners = new ArrayList<RecoveryListener>();
    private final List<BlockedListener> blockedListeners = new ArrayList<BlockedListener>();

    // Records topology changes
    private final Map<String, RecordedQueue> recordedQueues = new ConcurrentHashMap<String, RecordedQueue>();
    private final List<RecordedBinding> recordedBindings = new ArrayList<RecordedBinding>();
    private final Map<String, RecordedExchange> recordedExchanges = new ConcurrentHashMap<String, RecordedExchange>();
    private final Map<String, RecordedConsumer> consumers = new ConcurrentHashMap<String, RecordedConsumer>();
    private final List<ConsumerRecoveryListener> consumerRecoveryListeners = new ArrayList<ConsumerRecoveryListener>();
    private final List<QueueRecoveryListener> queueRecoveryListeners = new ArrayList<QueueRecoveryListener>();
	
	// Used to block connection recovery attempts after close() is invoked.
	private volatile boolean manuallyClosed = false;
	
	// This lock guards the manuallyClosed flag and the delegate connection.  Guarding these two ensures that a new connection can never
	// be created after application code has initiated shutdown.  
	private final Object recoveryLock = new Object();

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, List<Address> addrs) {
        this(params, f, new ListAddressResolver(addrs));
    }

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, AddressResolver addressResolver) {
        this(params, f, addressResolver, new NoOpMetricsCollector());
    }

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, AddressResolver addressResolver, MetricsCollector metricsCollector) {
        this.cf = new RecoveryAwareAMQConnectionFactory(params, f, addressResolver, metricsCollector);
        this.params = params;

        this.channels = new ConcurrentHashMap<Integer, AutorecoveringChannel>();
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
        if (ch == null) {
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
        final AutorecoveringChannel channel = new AutorecoveringChannel(this, delegateChannel);
        if (delegateChannel == null) {
            return null;
        } else {
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
        RecoveryCanBeginListener starter = new RecoveryCanBeginListener() {
            @Override
            public void recoveryCanBegin(ShutdownSignalException cause) {
                try {
                    if (shouldTriggerConnectionRecovery(cause)) {
                        c.beginAutomaticRecovery();
                    }
                } catch (Exception e) {
                    newConn.getExceptionHandler().handleConnectionRecoveryException(c, e);
                }
            }
        };
        synchronized (this) {
            newConn.addRecoveryCanBeginListener(starter);
        }
    }

    protected boolean shouldTriggerConnectionRecovery(ShutdownSignalException cause) {
        return !cause.isInitiatedByApplication() || (cause.getCause() instanceof MissedHeartbeatException);
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

    synchronized private void beginAutomaticRecovery() throws InterruptedException {
        Thread.sleep(this.params.getNetworkRecoveryInterval());
        final RecoveryAwareAMQConnection newConn = this.recoverConnection();
        if (newConn == null) {
            return;
        }

        this.addAutomaticRecoveryListener(newConn);
	    this.recoverShutdownListeners(newConn);
	    this.recoverBlockedListeners(newConn);
	    this.recoverChannels(newConn);
	    // don't assign new delegate connection until channel recovery is complete
	    this.delegate = newConn;
	    if(this.params.isTopologyRecoveryEnabled()) {
		      this.recoverEntities();
		      this.recoverConsumers();
	    }

	    this.notifyRecoveryListeners();
    }

    private void recoverShutdownListeners(final RecoveryAwareAMQConnection newConn) {
        for (ShutdownListener sh : this.shutdownHooks) {
            newConn.addShutdownListener(sh);
        }
    }

    private void recoverBlockedListeners(final RecoveryAwareAMQConnection newConn) {
        for (BlockedListener bl : this.blockedListeners) {
            newConn.addBlockedListener(bl);
        }
    }

	// Returns new connection if the connection was recovered, 
	// null if application initiated shutdown while attempting recovery.  
    private RecoveryAwareAMQConnection recoverConnection() throws InterruptedException {
        while (!manuallyClosed)
		{
            try {
				RecoveryAwareAMQConnection newConn = this.cf.newConnection();
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
                // TODO: exponential back-off
                Thread.sleep(this.params.getNetworkRecoveryInterval());
                this.getExceptionHandler().handleConnectionRecoveryException(this, e);
            }
        }
		
		return null;
    }

    private void recoverChannels(final RecoveryAwareAMQConnection newConn) {
        for (AutorecoveringChannel ch : this.channels.values()) {
            try {
                ch.automaticallyRecover(this, newConn);
            } catch (Throwable t) {
                newConn.getExceptionHandler().handleChannelRecoveryException(ch, t);
            }
        }
    }

    private void notifyRecoveryListeners() {
        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecovery(this);
        }
    }

    private void recoverEntities() {
        // The recovery sequence is the following:
        //
        // 1. Recover exchanges
        // 2. Recover queues
        // 3. Recover bindings
        // 4. Recover consumers
        recoverExchanges();
        recoverQueues();
        recoverBindings();
    }

    private void recoverExchanges() {
        // recorded exchanges are guaranteed to be
        // non-predefined (we filter out predefined ones
        // in exchangeDeclare). MK.
        for (RecordedExchange x : this.recordedExchanges.values()) {
            try {
                x.recover();
            } catch (Exception cause) {
                final String message = "Caught an exception while recovering exchange " + x.getName() +
                        ": " + cause.getMessage();
                TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, x.getDelegateChannel(), e);
            }
        }
    }

    private void recoverQueues() {
        Map<String, RecordedQueue> copy = new HashMap<String, RecordedQueue>(this.recordedQueues);
        for (Map.Entry<String, RecordedQueue> entry : copy.entrySet()) {
            String oldName = entry.getKey();
            RecordedQueue q = entry.getValue();
            try {
                q.recover();
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
                for(QueueRecoveryListener qrl : this.queueRecoveryListeners) {
                    qrl.queueRecovered(oldName, newName);
                }
            } catch (Exception cause) {
                final String message = "Caught an exception while recovering queue " + oldName +
                                               ": " + cause.getMessage();
                TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, q.getDelegateChannel(), e);
            }
        }
    }

    private void recoverBindings() {
        for (RecordedBinding b : this.recordedBindings) {
            try {
                b.recover();
            } catch (Exception cause) {
                String message = "Caught an exception while recovering binding between " + b.getSource() +
                                         " and " + b.getDestination() + ": " + cause.getMessage();
                TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, b.getDelegateChannel(), e);
            }
        }
    }

    private void recoverConsumers() {
        Map<String, RecordedConsumer> copy = new HashMap<String, RecordedConsumer>(this.consumers);
        for (Map.Entry<String, RecordedConsumer> entry : copy.entrySet()) {
            String tag = entry.getKey();
            RecordedConsumer consumer = entry.getValue();

            try {
                String newTag = consumer.recover();
                // make sure server-generated tags are re-added. MK.
                synchronized (this.consumers) {
                    this.consumers.remove(tag);
                    this.consumers.put(newTag, consumer);
                }
                for(ConsumerRecoveryListener crl : this.consumerRecoveryListeners) {
                    crl.consumerRecovered(tag, newTag);
                }
            } catch (Exception cause) {
                final String message = "Caught an exception while recovering consumer " + tag +
                        ": " + cause.getMessage();
                TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, consumer.getDelegateChannel(), e);
            }
        }
    }

    private void propagateQueueNameChangeToBindings(String oldName, String newName) {
        for (RecordedBinding b : this.recordedBindings) {
            if (b.getDestination().equals(oldName)) {
                b.setDestination(newName);
            }
        }
    }

    private void propagateQueueNameChangeToConsumers(String oldName, String newName) {
        for (RecordedConsumer c : this.consumers.values()) {
            if (c.getQueue().equals(oldName)) {
                c.setQueue(newName);
            }
        }
    }

    synchronized void recordQueueBinding(AutorecoveringChannel ch,
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

    synchronized boolean deleteRecordedQueueBinding(AutorecoveringChannel ch,
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

    synchronized void recordExchangeBinding(AutorecoveringChannel ch,
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

    synchronized boolean deleteRecordedExchangeBinding(AutorecoveringChannel ch,
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
        synchronized (this.recordedQueues) {
            synchronized (this.consumers) {
                if(!hasMoreConsumersOnQueue(this.consumers.values(), queue)) {
                    RecordedQueue q = this.recordedQueues.get(queue);
                    // last consumer on this connection is gone, remove recorded queue
                    // if it is auto-deleted. See bug 26364.
                    if((q != null) && q.isAutoDelete()) { this.recordedQueues.remove(queue); }
                }
            }
        }
    }

    void maybeDeleteRecordedAutoDeleteExchange(String exchange) {
        synchronized (this.recordedExchanges) {
            synchronized (this.consumers) {
                if(!hasMoreDestinationsBoundToExchange(this.recordedBindings, exchange)) {
                    RecordedExchange x = this.recordedExchanges.get(exchange);
                    // last binding where this exchange is the source is gone, remove recorded exchange
                    // if it is auto-deleted. See bug 26364.
                    if((x != null) && x.isAutoDelete()) { this.recordedExchanges.remove(exchange); }
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

    synchronized Set<RecordedBinding> removeBindingsWithDestination(String s) {
        Set<RecordedBinding> result = new HashSet<RecordedBinding>();
        for (Iterator<RecordedBinding> it = this.recordedBindings.iterator(); it.hasNext(); ) {
            RecordedBinding b = it.next();
            if(b.getDestination().equals(s)) {
                it.remove();
                result.add(b);
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
