package com.rabbitmq.client.impl.recovery;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.impl.ExceptionHandler;
import com.rabbitmq.client.impl.FrameHandlerFactory;
import com.rabbitmq.client.impl.NetworkConnection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * @see com.rabbitmq.client.impl.recovery.Recoverable
 * @see com.rabbitmq.client.ConnectionFactory#setAutomaticRecovery(boolean)
 * @see com.rabbitmq.client.ConnectionFactory#setTopologyRecovery(boolean)
 * @since 3.3.0
 */
public class AutorecoveringConnection implements Connection, Recoverable, NetworkConnection {
    private final RecoveryAwareAMQConnectionFactory cf;
    private final Map<Integer, AutorecoveringChannel> channels;
    private final List<ShutdownListener> shutdownHooks;
    private final List<RecoveryListener> recoveryListeners;
    private int networkRecoveryInterval;
    private RecoveryAwareAMQConnection delegate;
    private boolean topologyRecovery = true;

    // Records topology changes
    private final Map<String, RecordedQueue> recordedQueues = new ConcurrentHashMap<String, RecordedQueue>();
    private final List<RecordedBinding> recordedBindings = new ArrayList<RecordedBinding>();
    private Map<String, RecordedExchange> recordedExchanges = new ConcurrentHashMap<String, RecordedExchange>();
    private final Map<String, RecordedConsumer> consumers = new ConcurrentHashMap<String, RecordedConsumer>();

    public AutorecoveringConnection(ConnectionParams params, FrameHandlerFactory f, Address[] addrs) {
        this.cf = new RecoveryAwareAMQConnectionFactory(params, f, addrs);

        this.channels = new ConcurrentHashMap<Integer, AutorecoveringChannel>();
        this.shutdownHooks = new ArrayList<ShutdownListener>();
        this.recoveryListeners = new ArrayList<RecoveryListener>();
    }

    /**
     * Private API.
     * @throws IOException
     * @see com.rabbitmq.client.ConnectionFactory#newConnection(java.util.concurrent.ExecutorService)
     */
    public void init() throws IOException {
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

    /**
     * Private API.
     * @param channel {@link com.rabbitmq.client.impl.recovery.AutorecoveringChannel} to add
     *                to the list of channels to recover
     */
    private void registerChannel(AutorecoveringChannel channel) {
        this.channels.put(channel.getChannelNumber(), channel);
    }

    /**
     * Private API.
     * @param channel {@link com.rabbitmq.client.impl.recovery.AutorecoveringChannel} to remove
     *                from the list of channels to recover
     */
    public void unregisterChannel(AutorecoveringChannel channel) {
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
        delegate.close();
    }

    /**
     * @see Connection#close(int)
     */
    public void close(int timeout) throws IOException {
        delegate.close(timeout);
    }

    /**
     * @see Connection#close(int, String, int)
     */
    public void close(int closeCode, String closeMessage, int timeout) throws IOException {
        delegate.close(closeCode, closeMessage, timeout);
    }

    /**
     * @see com.rabbitmq.client.Connection#abort()
     */
    public void abort() {
        delegate.abort();
    }

    /**
     * @see Connection#abort(int, String, int)
     */
    public void abort(int closeCode, String closeMessage, int timeout) {
        delegate.abort(closeCode, closeMessage, timeout);
    }

    /**
     * @see Connection#abort(int, String)
     */
    public void abort(int closeCode, String closeMessage) {
        delegate.abort(closeCode, closeMessage);
    }

    /**
     * @see Connection#abort(int)
     */
    public void abort(int timeout) {
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
        delegate.addBlockedListener(listener);
    }

    /**
     * @see Connection#removeBlockedListener(com.rabbitmq.client.BlockedListener)
     */
    public boolean removeBlockedListener(BlockedListener listener) {
        return delegate.removeBlockedListener(listener);
    }

    /**
     * @see com.rabbitmq.client.Connection#clearBlockedListeners()
     */
    public void clearBlockedListeners() {
        delegate.clearBlockedListeners();
    }

    /**
     * @see com.rabbitmq.client.Connection#close(int, String)
     */
    public void close(int closeCode, String closeMessage) throws IOException {
        delegate.close(closeCode, closeMessage);
    }

    /**
     * @see Connection#addShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void addShutdownListener(ShutdownListener listener) {
        delegate.addShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#removeShutdownListener(com.rabbitmq.client.ShutdownListener)
     */
    public void removeShutdownListener(ShutdownListener listener) {
        delegate.removeShutdownListener(listener);
    }

    /**
     * @see com.rabbitmq.client.ShutdownNotifier#notifyListeners()
     */
    public void notifyListeners() {
        delegate.notifyListeners();
    }

    @SuppressWarnings("unused")
    public boolean isTopologyRecoveryEnabled() {
        return this.topologyRecovery;
    }

    /**
     * Private API.
     * @param topologyRecovery if true, enables topology recovery
     */
    public void setTopologyRecovery(boolean topologyRecovery) {
        this.topologyRecovery = topologyRecovery;
    }

    public void setNetworkRecoveryInterval(int networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }

    /**
     * Adds the recovery listener
     * @param listener {@link com.rabbitmq.client.impl.recovery.RecoveryListener} to execute after this connection recovers from network failure
     */
    public void addRecoveryListener(RecoveryListener listener) {
        this.recoveryListeners.add(listener);
    }

    /**
     * Removes the recovery listener
     * @param listener {@link com.rabbitmq.client.impl.recovery.RecoveryListener} to remove
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
                    if (!cause.isInitiatedByApplication()) {
                        c.beginAutomaticRecovery();
                    }
                } catch (Exception e) {
                    c.delegate.getExceptionHandler().handleConnectionRecoveryException(c, e);
                }
            }
        };
        synchronized (this) {
            this.shutdownHooks.add(automaticRecoveryListener);
            this.delegate.addShutdownListener(automaticRecoveryListener);
        }
    }

    synchronized private void beginAutomaticRecovery() throws InterruptedException, IOException, TopologyRecoveryException {
        Thread.sleep(networkRecoveryInterval);
        this.recoverConnection();
        this.recoverShutdownHooks();
        this.recoverChannels();
        if(topologyRecovery) {
            this.recoverEntites();
            this.recoverConsumers();
        }

        this.notifyRecoveryListeners();
    }

    private void recoverShutdownHooks() {
        for (ShutdownListener sh : this.shutdownHooks) {
            this.delegate.addShutdownListener(sh);
        }
    }

    private void recoverConnection() throws IOException, InterruptedException {
        boolean recovering = true;
        while (recovering) {
            try {
                this.delegate = (RecoveryAwareAMQConnection) this.cf.newConnection();
                recovering = false;
            } catch (ConnectException ce) {
                System.err.println("Failed to reconnect: " + ce.getMessage());
                // TODO: exponential back-off
                Thread.sleep(networkRecoveryInterval);
            }
        }
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

    private void recoverEntites() throws TopologyRecoveryException {
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
                TopologyRecoveryException e = new TopologyRecoveryException("Caught an exception while recovering exchange " + x.getName(), cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, x.getDelegateChannel(), e);
            }
        }
    }

    private void recoverQueues() {
        for (Map.Entry<String, RecordedQueue> entry : this.recordedQueues.entrySet()) {
            String oldName = entry.getKey();
            RecordedQueue q = entry.getValue();
            try {
                q.recover();
                String newName = q.getName();
                // make sure server-named queues are re-added with
                // their new names. MK.
                synchronized (this.recordedQueues) {
                    deleteRecordedQueue(oldName);
                    this.recordedQueues.put(newName, q);
                    this.propagateQueueNameChangeToBindings(oldName, newName);
                    this.propagateQueueNameChangeToConsumers(oldName, newName);
                }
            } catch (Exception cause) {
                TopologyRecoveryException e = new TopologyRecoveryException("Caught an exception while recovering queue " + oldName, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, q.getDelegateChannel(), e);
            }
        }
    }

    private void recoverBindings() {
        for (RecordedBinding b : this.recordedBindings) {
            try {
                b.recover();
            } catch (Exception cause) {
                String message = "Caught an exception while recovering binding between " + b.getSource() + " and " + b.getDestination();
                TopologyRecoveryException e = new TopologyRecoveryException(message, cause);
                this.getExceptionHandler().handleTopologyRecoveryException(delegate, b.getDelegateChannel(), e);
            }
        }
    }

    private void recoverConsumers() {
        for (Map.Entry<String, RecordedConsumer> entry : this.consumers.entrySet()) {
            String tag = entry.getKey();
            RecordedConsumer consumer = entry.getValue();

            try {
                String newTag = consumer.recover();
                // make sure server-generated tags are re-added. MK.
                synchronized (this.consumers) {
                    this.consumers.remove(tag);
                    this.consumers.put(newTag, consumer);
                }
            } catch (Exception cause) {
                TopologyRecoveryException e = new TopologyRecoveryException("Caught an exception while recovering consumer " + tag, cause);
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

    void deleteRecordedQueue(String queue) {
        this.recordedQueues.remove(queue);
    }

    void recordExchange(String exchange, RecordedExchange x) {
        this.recordedExchanges.put(exchange, x);
    }

    void deleteRecordedExchange(String exchange) {
        this.recordedExchanges.remove(exchange);
    }

    void recordConsumer(String result, RecordedConsumer consumer) {
        this.consumers.put(result, consumer);
    }

    void deleteRecordedConsumer(String consumerTag) {
        this.consumers.remove(consumerTag);
    }
}
