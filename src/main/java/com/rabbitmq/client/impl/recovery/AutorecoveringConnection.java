package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MissedHeartbeatException;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.TopologyRecoveryException;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import com.rabbitmq.client.impl.NetworkConnection;

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
	
    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, Address[] addrs) {
        this.cf = new RecoveryAwareAMQConnectionFactory(params, f, addrs);
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
        this.addAutomaticRecoveryListener();
    }

    public void start() throws IOException {
        // no-op, AMQConnection#start is executed in ConnectionFactory#newConnection
        // and invoking it again will result in a framing error. MK.
    }

    /**
     * @see com.rabbitmq.client.Connection#createChannel()
     */
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
    public Map<String, Object> getServerProperties() {
        return delegate.getServerProperties();
    }

    /**
     * @see com.rabbitmq.client.Connection#getClientProperties()
     */
    public Map<String, Object> getClientProperties() {
        return delegate.getClientProperties();
    }

    /**
     * @see com.rabbitmq.client.Connection#getFrameMax()
     */
    public int getFrameMax() {
        return delegate.getFrameMax();
    }

    /**
     * @see com.rabbitmq.client.Connection#getHeartbeat()
     */
    public int getHeartbeat() {
        return delegate.getHeartbeat();
    }

    /**
     * @see com.rabbitmq.client.Connection#getChannelMax()
     */
    public int getChannelMax() {
        return delegate.getChannelMax();
    }

    /**
     * @see com.rabbitmq.client.Connection#isOpen()
     */
    public boolean isOpen() {
        return delegate.isOpen();
    }

    /**
     * @see com.rabbitmq.client.Connection#close()
     */
    public void close() throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close();
    }

    /**
     * @see Connection#close(int)
     */
    public void close(int timeout) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close(timeout);
    }

    /**
     * @see Connection#close(int, String, int)
     */
    public void close(int closeCode, String closeMessage, int timeout) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.close(closeCode, closeMessage, timeout);
    }

    /**
     * @see com.rabbitmq.client.Connection#abort()
     */
    public void abort() {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort();
    }

    /**
     * @see Connection#abort(int, String, int)
     */
    public void abort(int closeCode, String closeMessage, int timeout) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(closeCode, closeMessage, timeout);
    }

    /**
     * @see Connection#abort(int, String)
     */
    public void abort(int closeCode, String closeMessage) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(closeCode, closeMessage);
    }

    /**
     * @see Connection#abort(int)
     */
    public void abort(int timeout) {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
        delegate.abort(timeout);
    }

    /**
     * @see com.rabbitmq.client.Connection#getCloseReason()
     */
    public ShutdownSignalException getCloseReason() {
        return delegate.getCloseReason();
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void addBlockedListener(BlockedListener listener) {
        this.blockedListeners.add(listener);
        delegate.addBlockedListener(listener);
    }

    /**
     * @see Connection#removeBlockedListener(com.rabbitmq.client.BlockedListener)
     */
    public boolean removeBlockedListener(BlockedListener listener) {
        this.blockedListeners.remove(listener);
        return delegate.removeBlockedListener(listener);
    }

    /**
     * @see com.rabbitmq.client.Connection#clearBlockedListeners()
     */
    public void clearBlockedListeners() {
        this.blockedListeners.clear();
        delegate.clearBlockedListeners();
    }

    /**
     * @see com.rabbitmq.client.Connection#close(int, String)
     */
    public void close(int closeCode, String closeMessage) throws IOException {
		synchronized(recoveryLock) {
			this.manuallyClosed = true;
		}
		delegate.close(closeCode, closeMessage);
    }

    /**
     * @see Connection#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void addShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.add(listener);
        delegate.addShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#removeShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void removeShutdownListener(ShutdownListener listener) {
        this.shutdownHooks.remove(listener);
        delegate.removeShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#notifyListeners()
     */
    public void notifyListeners() {
        delegate.notifyListeners();
    }

    /**
     * Adds the recovery listener
     * @param listener {@link com.rabbitmq.client.RecoveryListener} to execute after this connection recovers from network failure
     */
    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    /**
     * Removes the recovery listener
     * @param listener {@link com.rabbitmq.client.RecoveryListener} to remove
     */
    public void removeRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.remove(listener);
    }

    /**
     * @see com.rabbitmq.client.impl.AMQConnection#getExceptionHandler()
     */
    @SuppressWarnings("unused")
    public ExceptionHandler getExceptionHandler() {
        return this.delegate.getExceptionHandler();
    }

    /**
     * @see com.rabbitmq.client.Connection#getPort()
     */
    public int getPort() {
        return delegate.getPort();
    }

    /**
     * @see com.rabbitmq.client.Connection#getAddress()
     */
    public InetAddress getAddress() {
        return delegate.getAddress();
    }

    /**
     * @return client socket address
     */
    public InetAddress getLocalAddress() {
        return this.delegate.getLocalAddress();
    }

    /**
     * @return client socket port
     */
    public int getLocalPort() {
        return this.delegate.getLocalPort();
    }

    //
    // Recovery
    //

    private void addAutomaticRecoveryListener() {
        final AutorecoveringConnection c = this;
        ShutdownListener automaticRecoveryListener = new ShutdownListener() {
            public void shutdownCompleted(ShutdownSignalException cause) {
                try {
                    if (shouldTriggerConnectionRecovery(cause)) {
                        c.beginAutomaticRecovery();
                    }
                } catch (Exception e) {
                    c.delegate.getExceptionHandler().handleConnectionRecoveryException(c, e);
                }
            }
        };
        synchronized (this) {
            if(!this.shutdownHooks.contains(automaticRecoveryListener)) {
                this.shutdownHooks.add(automaticRecoveryListener);
            }
            this.delegate.addShutdownListener(automaticRecoveryListener);
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public void removeConsumerRecoveryListener(ConsumerRecoveryListener listener) {
        this.consumerRecoveryListeners.remove(listener);
    }

    synchronized private void beginAutomaticRecovery() throws InterruptedException, IOException, TopologyRecoveryException {
        Thread.sleep(this.params.getNetworkRecoveryInterval());
        if (!this.recoverConnection())
			return; 
		
		this.recoverShutdownListeners();
		this.recoverBlockedListeners();
		this.recoverChannels();
		if(this.params.isTopologyRecoveryEnabled()) {
			this.recoverEntities();
			this.recoverConsumers();
		}

		this.notifyRecoveryListeners();
    }

    private void recoverShutdownListeners() {
        for (ShutdownListener sh : this.shutdownHooks) {
            this.delegate.addShutdownListener(sh);
        }
    }

    private void recoverBlockedListeners() {
        for (BlockedListener bl : this.blockedListeners) {
            this.delegate.addBlockedListener(bl);
        }
    }

	// Returns true if the connection was recovered, 
	// false if application initiated shutdown while attempting recovery.  
    private boolean recoverConnection() throws IOException, InterruptedException {
        while (!manuallyClosed)
		{
            try {
				RecoveryAwareAMQConnection newConn = this.cf.newConnection();
				synchronized(recoveryLock) {
					if (!manuallyClosed) {
						// This is the standard case.
						this.delegate = newConn;					
						return true;
					}
				}
				// This is the once in a blue moon case.  
				// Application code just called close as the connection
				// was being re-established.  So we attempt to close the newly created connection.
				newConn.abort();
				return false;
            } catch (Exception e) {
                // TODO: exponential back-off
                Thread.sleep(this.params.getNetworkRecoveryInterval());
                this.getExceptionHandler().handleConnectionRecoveryException(this, e);
            }
        }
		
		return false;
    }

    private void recoverChannels() {
        for (AutorecoveringChannel ch : this.channels.values()) {
            try {
                ch.automaticallyRecover(this, this.delegate);
            } catch (Throwable t) {
                this.delegate.getExceptionHandler().handleChannelRecoveryException(ch, t);
            }
        }
    }

    private void notifyRecoveryListeners() {
        for (RecoveryListener f : this.recoveryListeners) {
            f.handleRecovery(this);
        }
    }

    private void recoverEntities() throws TopologyRecoveryException {
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
        if (!this.recordedBindings.contains(binding)) {
            this.recordedBindings.add(binding);
        }
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

    Set<RecordedBinding> removeBindingsWithDestination(String s) {
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
}
